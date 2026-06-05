param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$MysqlContainer = "expense-mysql-dev",
    [string]$MysqlDatabase = "",
    [string]$MysqlUser = "",
    [string]$MysqlPassword = "",
    [switch]$SkipCleanup
)

$ErrorActionPreference = "Stop"

function Use-Default {
    param(
        [string]$Value,
        [string]$Fallback
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $Fallback
    }
    return $Value
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-ApiJson {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null,
        [hashtable]$Headers = $null
    )

    $params = @{
        Method = $Method
        Uri = $Uri
        ContentType = "application/json"
    }
    if ($Headers) {
        $params.Headers = $Headers
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }

    Invoke-RestMethod @params
}

function Escape-SqlString {
    param([string]$Value)

    return $Value.Replace("\", "\\").Replace("'", "''")
}

function Cleanup-SmokeUser {
    param(
        [string]$Username
    )

    if ($SkipCleanup) {
        Write-Warning "Cleanup skipped. Smoke user may remain: $Username"
        return
    }

    if ($Username -notmatch '^smoke_[0-9]+$') {
        throw "Refusing to clean up non-smoke username: $Username"
    }

    $dbName = Use-Default $MysqlDatabase (Use-Default $env:MYSQL_DATABASE "expense_tracker")
    $dbUser = Use-Default $MysqlUser (Use-Default $env:MYSQL_USER "expense_app")
    $dbPassword = Use-Default $MysqlPassword (Use-Default $env:MYSQL_PASSWORD "change-me-app")
    $safeUsername = Escape-SqlString $Username

    $smokeEmail = "$safeUsername@example.invalid"
    $safeEmail = Escape-SqlString $smokeEmail

    $sql = @"
START TRANSACTION;
SET @smoke_username = '$safeUsername';
SET @smoke_email = '$safeEmail';
SET @smoke_user_id = (
  SELECT id
  FROM users
  WHERE username = @smoke_username
    AND username REGEXP '^smoke_[0-9]+$'
  LIMIT 1
);
SELECT COALESCE(@smoke_user_id, 0) AS cleanup_user_id;
DELETE FROM auth_challenges
WHERE (user_id = @smoke_user_id OR email = @smoke_email)
  AND (email = @smoke_email OR @smoke_user_id <> 0);
SELECT ROW_COUNT() AS deleted_auth_challenges;
DELETE FROM refresh_tokens WHERE user_id = @smoke_user_id;
SELECT ROW_COUNT() AS deleted_refresh_tokens;
DELETE FROM import_jobs WHERE user_id = @smoke_user_id;
SELECT ROW_COUNT() AS deleted_import_jobs;
DELETE FROM budgets WHERE user_id = @smoke_user_id;
SELECT ROW_COUNT() AS deleted_budgets;
DELETE FROM transactions WHERE user_id = @smoke_user_id;
SELECT ROW_COUNT() AS deleted_transactions;
DELETE FROM categories WHERE user_id = @smoke_user_id;
SELECT ROW_COUNT() AS deleted_categories;
DELETE FROM payment_methods WHERE user_id = @smoke_user_id;
SELECT ROW_COUNT() AS deleted_payment_methods;
DELETE FROM users
WHERE id = @smoke_user_id
  AND username = @smoke_username
  AND username REGEXP '^smoke_[0-9]+$';
SELECT ROW_COUNT() AS deleted_users;
COMMIT;
SELECT COUNT(*) AS remaining_smoke_user
FROM users
WHERE username = @smoke_username;
"@

    $output = $sql | docker exec -i --env "MYSQL_PWD=$dbPassword" $MysqlContainer mysql "-u$dbUser" $dbName --batch --raw
    Write-Host $output

    if ($LASTEXITCODE -ne 0) {
        throw "Smoke cleanup failed for user: $Username"
    }
    if (($output -join "`n") -notmatch "remaining_smoke_user\s+0") {
        throw "Smoke cleanup did not remove user: $Username"
    }
}

function Invoke-SmokeSql {
    param(
        [string]$Sql
    )

    $dbName = Use-Default $MysqlDatabase (Use-Default $env:MYSQL_DATABASE "expense_tracker")
    $dbUser = Use-Default $MysqlUser (Use-Default $env:MYSQL_USER "expense_app")
    $dbPassword = Use-Default $MysqlPassword (Use-Default $env:MYSQL_PASSWORD "change-me-app")

    $output = $Sql | docker exec -i --env "MYSQL_PWD=$dbPassword" $MysqlContainer mysql "-u$dbUser" $dbName --batch --raw
    if ($LASTEXITCODE -ne 0) {
        throw "Smoke SQL command failed"
    }
    return $output
}

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$username = "smoke_$stamp"
$email = "$username@example.invalid"
$password = "SmokeTest123!"
$emailCode = "123456"
$headers = $null
$transactionId = $null
$budgetId = $null
$cleanupError = $null

try {
    Write-Host "Running local smoke test against $BaseUrl"

    $emailCodeResponse = Invoke-ApiJson "Post" "$BaseUrl/auth/register/email-code" @{
        email = $email
    }
    Assert-True $emailCodeResponse.success "register email code request failed"

    $safeEmail = Escape-SqlString $email
    $seedCodeSql = @"
UPDATE auth_challenges
SET code_hash = SHA2('$emailCode', 256),
    expires_at = DATE_ADD(NOW(), INTERVAL 10 MINUTE),
    attempt_count = 0,
    consumed_at = NULL
WHERE email = '$safeEmail'
  AND purpose = 'REGISTER'
  AND consumed_at IS NULL
ORDER BY sent_at DESC, id DESC
LIMIT 1;
SELECT ROW_COUNT() AS updated_register_challenge;
"@
    $seedCodeOutput = Invoke-SmokeSql $seedCodeSql
    Assert-True (($seedCodeOutput -join "`n") -match "updated_register_challenge\s+1") "register email code challenge was not prepared"

    $register = Invoke-ApiJson "Post" "$BaseUrl/auth/register" @{
        username = $username
        password = $password
        nickname = "Smoke Test"
        email = $email
        emailCode = $emailCode
    }
    Assert-True $register.success "register failed"
    Assert-True (-not [string]::IsNullOrWhiteSpace($register.data.accessToken)) "missing access token"
    Assert-True (-not [string]::IsNullOrWhiteSpace($register.data.refreshToken)) "missing refresh token"

    $headers = @{ Authorization = "Bearer $($register.data.accessToken)" }

    $me = Invoke-ApiJson "Get" "$BaseUrl/auth/me" $null $headers
    Assert-True ($me.data.username -eq $username) "auth me did not return the registered user"

    $categories = Invoke-ApiJson "Get" "$BaseUrl/categories" $null $headers
    $expenseCategory = @($categories.data | Where-Object { $_.type -eq "EXPENSE" } | Select-Object -First 1)[0]
    Assert-True ($null -ne $expenseCategory) "no default expense category found"

    $payments = Invoke-ApiJson "Get" "$BaseUrl/payment-methods" $null $headers
    $payment = @($payments.data | Select-Object -First 1)[0]
    Assert-True ($null -ne $payment) "no default payment method found"

    $occurredAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
    $month = (Get-Date).ToString("yyyy-MM")
    $year = (Get-Date).Year
    $itemName = "Smoke Expense $stamp"

    $transaction = Invoke-ApiJson "Post" "$BaseUrl/transactions" @{
        type = "EXPENSE"
        itemName = $itemName
        amount = 12.34
        occurredAt = $occurredAt
        channel = "ONLINE"
        onlineApp = "SmokeApp"
        offlinePlace = $null
        categoryId = $expenseCategory.id
        paymentMethodId = $payment.id
        note = "smoke test"
    } $headers
    Assert-True ($null -ne $transaction.data.id) "transaction create returned no id"
    $transactionId = $transaction.data.id

    $detail = Invoke-ApiJson "Get" "$BaseUrl/transactions/$transactionId" $null $headers
    Assert-True ($detail.data.itemName -eq $itemName) "transaction detail mismatch"

    $list = Invoke-ApiJson "Get" "$BaseUrl/transactions?keyword=Smoke&page=1&size=10" $null $headers
    Assert-True ($list.data.total -ge 1) "transaction list did not include created transaction"

    $dailyCards = Invoke-ApiJson "Get" "$BaseUrl/transactions/daily-cards?keyword=Smoke&dayPage=1&daySize=5&recordPage=1&recordSize=5" $null $headers
    Assert-True ($dailyCards.data.totalRecords -ge 1) "daily cards totalRecords did not include created transaction"
    Assert-True ($dailyCards.data.totalDays -ge 1) "daily cards totalDays did not include created transaction date"
    $cardRecordNames = @($dailyCards.data.days | ForEach-Object { $_.records.records } | ForEach-Object { $_.itemName })
    Assert-True ($cardRecordNames -contains $itemName) "daily cards records did not include created transaction"

    $dailyOptions = Invoke-ApiJson "Get" "$BaseUrl/transactions/daily-options?keyword=Smoke" $null $headers
    Assert-True (@($dailyOptions.data).Count -ge 1) "daily options did not include created transaction date"

    $monthly = Invoke-ApiJson "Get" "$BaseUrl/statistics/monthly?month=$month" $null $headers
    Assert-True ([decimal]$monthly.data.totalExpense -ge 12.34) "monthly statistics totalExpense did not include created transaction"

    $yearly = Invoke-ApiJson "Get" "$BaseUrl/statistics/yearly?year=$year" $null $headers
    Assert-True ([decimal]$yearly.data.totalExpense -ge 12.34) "yearly statistics totalExpense did not include created transaction"

    $budget = Invoke-ApiJson "Post" "$BaseUrl/budgets" @{
        month = $month
        categoryId = $expenseCategory.id
        amount = 1000.00
    } $headers
    Assert-True ($null -ne $budget.data.id) "budget create returned no id"
    $budgetId = $budget.data.id

    $budgetList = Invoke-ApiJson "Get" "$BaseUrl/budgets?month=$month" $null $headers
    Assert-True (@($budgetList.data).Count -ge 1) "budget list did not include created budget"

    $export = Invoke-WebRequest -Method Get -Uri "$BaseUrl/exports/transactions.csv?keyword=Smoke" -Headers $headers -UseBasicParsing
    Assert-True ($export.StatusCode -eq 200) "csv export did not return 200"
    Assert-True ([string]$export.Content -match "Smoke Expense") "csv export did not include created transaction"

    $logout = Invoke-ApiJson "Post" "$BaseUrl/auth/logout" @{ refreshToken = $register.data.refreshToken } $headers
    Assert-True $logout.success "logout failed"

    Write-Host "Smoke checks passed for $username"
    Write-Host "Created transaction: $transactionId"
    Write-Host "Created budget: $budgetId"
}
finally {
    try {
        Cleanup-SmokeUser $username
    }
    catch {
        $cleanupError = $_
    }
}

if ($cleanupError) {
    throw $cleanupError
}

Write-Host "Smoke cleanup completed for $username"
