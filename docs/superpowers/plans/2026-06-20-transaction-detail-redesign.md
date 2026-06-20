# Transaction Detail Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `/records/:id` as a ledger-style detail page with a clearer read state, a matching edit state, and tested presentation helpers.

**Architecture:** Keep the page in `frontend/src/views/TransactionDetailView.vue`, but extract pure display derivation into `frontend/src/utils/transactionDetailPresentation.ts` so date formatting, summary chips, and ledger items are testable outside Vue. The Vue file keeps existing data loading, editing, image, and submit behavior, while its template and scoped styles are reorganized around summary, ledger, image, and action sections.

**Tech Stack:** Vue 3 `<script setup>`, TypeScript, Vite, Vant, existing project CSS tokens, Node `assert` tests run directly with `node`.

---

## File Structure

- Create: `frontend/src/utils/transactionDetailPresentation.ts`
  - Pure presentation helpers for `TransactionRecord`.
  - No Vue imports and no network or storage access.
- Create: `frontend/tests/transactionDetailPresentation.test.ts`
  - Node assertions for Chinese date formatting, online/offline place labels, summary chips, and ledger item order.
- Modify: `frontend/src/views/TransactionDetailView.vue`
  - Import the helper functions.
  - Replace read-state template with the ledger-style layout.
  - Reorganize edit-state template to follow the same field order.
  - Replace old read-state CSS with new token-based classes.
- Keep unchanged: API service methods, `TransactionPayload`, image upload limits, `BottomSheet`, `FormActionBar`, `ModernDateField`, and `AmapPlaceField`.

## Task 1: Add Tested Presentation Helpers

**Files:**
- Create: `frontend/tests/transactionDetailPresentation.test.ts`
- Create: `frontend/src/utils/transactionDetailPresentation.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/tests/transactionDetailPresentation.test.ts` with:

```ts
import { strict as assert } from 'node:assert'
import {
  displayTransactionDateTime,
  transactionChannelText,
  transactionImageCountText,
  transactionLedgerItems,
  transactionPlaceLabel,
  transactionPlaceValue,
  transactionSummaryChips,
  transactionTypeText
} from '../src/utils/transactionDetailPresentation.ts'
import type { TransactionRecord } from '../src/types.ts'

const offlineRecord: TransactionRecord = {
  id: 12,
  type: 'EXPENSE',
  itemName: '午餐',
  amount: 36.8,
  occurredAt: '2026-06-20T12:24:30',
  channel: 'OFFLINE',
  offlinePlace: '港式茶餐厅',
  paymentMethodId: 2,
  paymentMethodName: '招商银行卡',
  categoryId: 1,
  categoryName: '餐饮',
  categoryIcon: 'shop-o',
  note: '和同事吃午饭，含饮品。',
  images: [
    {
      id: 7,
      originalFilename: 'receipt.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 1024,
      url: '',
      sortOrder: 0
    }
  ]
}

const onlineIncomeRecord: TransactionRecord = {
  ...offlineRecord,
  id: 13,
  type: 'INCOME',
  itemName: '',
  amount: 8800,
  occurredAt: '2026-06-20 09:05:00',
  channel: 'ONLINE',
  onlineApp: '公司系统',
  offlinePlace: undefined,
  paymentMethodName: '工资卡',
  categoryName: '工资',
  note: '',
  images: []
}

assert.equal(displayTransactionDateTime('2026-06-20T12:24:30'), '2026年06月20日 12:24')
assert.equal(displayTransactionDateTime('2026-06-20 09:05:00'), '2026年06月20日 09:05')
assert.equal(displayTransactionDateTime('bad-value'), 'bad-value')

assert.equal(transactionTypeText(offlineRecord), '支出')
assert.equal(transactionTypeText(onlineIncomeRecord), '收入')
assert.equal(transactionChannelText(offlineRecord), '线下')
assert.equal(transactionChannelText(onlineIncomeRecord), '线上')

assert.equal(transactionPlaceLabel(offlineRecord), '线下地点')
assert.equal(transactionPlaceValue(offlineRecord), '港式茶餐厅')
assert.equal(transactionPlaceLabel(onlineIncomeRecord), '线上平台')
assert.equal(transactionPlaceValue(onlineIncomeRecord), '公司系统')

assert.equal(transactionImageCountText(offlineRecord), '1 张凭证')
assert.equal(transactionImageCountText(onlineIncomeRecord), '无凭证')

assert.deepEqual(transactionSummaryChips(offlineRecord), [
  '支出',
  '线下',
  '餐饮',
  '招商银行卡',
  '港式茶餐厅',
  '1 张凭证'
])

assert.deepEqual(transactionSummaryChips(onlineIncomeRecord), [
  '收入',
  '线上',
  '工资',
  '工资卡',
  '公司系统'
])

assert.deepEqual(transactionLedgerItems(offlineRecord), [
  {
    key: 'occurredAt',
    icon: 'clock-o',
    label: '发生时间',
    value: '2026年06月20日 12:24'
  },
  {
    key: 'category',
    icon: 'apps-o',
    label: '分类',
    value: '餐饮',
    description: '支出分类'
  },
  {
    key: 'payment',
    icon: 'balance-o',
    label: '支付方式',
    value: '招商银行卡'
  },
  {
    key: 'place',
    icon: 'location-o',
    label: '线下地点',
    value: '港式茶餐厅',
    description: '线下'
  },
  {
    key: 'note',
    icon: 'comment-o',
    label: '备注',
    value: '和同事吃午饭，含饮品。'
  }
])

assert.equal(transactionLedgerItems(onlineIncomeRecord)[4].value, '无备注')
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd frontend
node tests/transactionDetailPresentation.test.ts
```

Expected: FAIL with a module-not-found error for `transactionDetailPresentation.ts`.

- [ ] **Step 3: Write the minimal implementation**

Create `frontend/src/utils/transactionDetailPresentation.ts` with:

```ts
import type { TransactionRecord } from '@/types'

export interface TransactionLedgerItem {
  key: 'occurredAt' | 'category' | 'payment' | 'place' | 'note'
  icon: string
  label: string
  value: string
  description?: string
}

function compactText(value: string | undefined | null) {
  return value?.trim() || ''
}

export function displayTransactionDateTime(value: string | undefined | null) {
  if (!value) return '-'
  const normalized = value.replace('T', ' ').slice(0, 16)
  const match = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}:\d{2})$/.exec(normalized)
  if (!match) return normalized
  return `${match[1]}年${match[2]}月${match[3]}日 ${match[4]}`
}

export function transactionTypeText(record: Pick<TransactionRecord, 'type'>) {
  return record.type === 'INCOME' ? '收入' : '支出'
}

export function transactionChannelText(record: Pick<TransactionRecord, 'channel'>) {
  return record.channel === 'ONLINE' ? '线上' : '线下'
}

export function transactionPlaceLabel(record: Pick<TransactionRecord, 'channel'>) {
  return record.channel === 'ONLINE' ? '线上平台' : '线下地点'
}

export function transactionPlaceValue(record: Pick<TransactionRecord, 'channel' | 'onlineApp' | 'offlinePlace'>) {
  return record.channel === 'ONLINE'
    ? compactText(record.onlineApp) || '未填写'
    : compactText(record.offlinePlace) || '未填写'
}

export function transactionImageCountText(record: Pick<TransactionRecord, 'images'>) {
  const count = record.images?.length ?? 0
  return count > 0 ? `${count} 张凭证` : '无凭证'
}

export function transactionSummaryChips(record: TransactionRecord) {
  const chips = [
    transactionTypeText(record),
    transactionChannelText(record),
    record.categoryName,
    record.paymentMethodName,
    transactionPlaceValue(record)
  ]
  const imageCount = record.images?.length ?? 0
  if (imageCount > 0) {
    chips.push(transactionImageCountText(record))
  }
  return chips.filter((item) => compactText(item))
}

export function transactionLedgerItems(record: TransactionRecord): TransactionLedgerItem[] {
  const channel = transactionChannelText(record)
  const placeLabel = transactionPlaceLabel(record)
  return [
    {
      key: 'occurredAt',
      icon: 'clock-o',
      label: '发生时间',
      value: displayTransactionDateTime(record.occurredAt)
    },
    {
      key: 'category',
      icon: record.categoryIcon || 'apps-o',
      label: '分类',
      value: compactText(record.categoryName) || '未分类',
      description: `${transactionTypeText(record)}分类`
    },
    {
      key: 'payment',
      icon: 'balance-o',
      label: '支付方式',
      value: compactText(record.paymentMethodName) || '未填写'
    },
    {
      key: 'place',
      icon: record.channel === 'ONLINE' ? 'shopping-cart-o' : 'location-o',
      label: placeLabel,
      value: transactionPlaceValue(record),
      description: channel
    },
    {
      key: 'note',
      icon: 'comment-o',
      label: '备注',
      value: compactText(record.note) || '无备注'
    }
  ]
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
cd frontend
node tests/transactionDetailPresentation.test.ts
```

Expected: PASS with no output.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/utils/transactionDetailPresentation.ts frontend/tests/transactionDetailPresentation.test.ts
git commit -m "新增记录详情展示工具"
```

## Task 2: Wire Helpers Into the Detail Page Script

**Files:**
- Modify: `frontend/src/views/TransactionDetailView.vue`

- [ ] **Step 1: Write the failing type-check expectation**

Run:

```bash
cd frontend
npm run type-check
```

Expected: PASS before edits. This establishes the baseline.

- [ ] **Step 2: Update imports**

In `frontend/src/views/TransactionDetailView.vue`, replace:

```ts
import { money, nowLocalInput, toBackendDateTime, toDateTimeLocal } from '@/utils/date'
```

with:

```ts
import { money, nowLocalInput, toBackendDateTime, toDateTimeLocal } from '@/utils/date'
import {
  displayTransactionDateTime,
  transactionChannelText,
  transactionImageCountText,
  transactionLedgerItems,
  transactionPlaceLabel,
  transactionPlaceValue,
  transactionSummaryChips,
  transactionTypeText
} from '@/utils/transactionDetailPresentation'
```

- [ ] **Step 3: Replace detail computed values**

Replace the existing `detailTypeText`, `detailChannelText`, `detailPlaceLabel`, and `detailPlaceValue` computed declarations with:

```ts
const detailTypeText = computed(() => record.value ? transactionTypeText(record.value) : '')
const detailChannelText = computed(() => record.value ? transactionChannelText(record.value) : '')
const detailPlaceLabel = computed(() => record.value ? transactionPlaceLabel(record.value) : '')
const detailPlaceValue = computed(() => record.value ? transactionPlaceValue(record.value) : '')
const detailSummaryChips = computed(() => record.value ? transactionSummaryChips(record.value) : [])
const detailLedgerItems = computed(() => record.value ? transactionLedgerItems(record.value) : [])
const detailImageCountText = computed(() => record.value ? transactionImageCountText(record.value) : '无凭证')
```

- [ ] **Step 4: Replace local date display helper**

Replace:

```ts
function displayDateTime(value: string) {
  return value.replace('T', ' ')
}
```

with:

```ts
function displayDateTime(value: string) {
  return displayTransactionDateTime(value)
}
```

- [ ] **Step 5: Run type-check**

Run:

```bash
cd frontend
npm run type-check
```

Expected: PASS. If it fails because a computed value is unused, keep it only when it is used by the template tasks below.

## Task 3: Rebuild the Read-State Template

**Files:**
- Modify: `frontend/src/views/TransactionDetailView.vue`

- [ ] **Step 1: Replace the read-state template block**

Inside `<template v-else-if="record && !editMode">`, replace the existing hero, action, info, and image sections with:

```vue
        <section
          :class="[
            'section',
            'panel',
            'detail-summary',
            record.type === 'EXPENSE' ? 'detail-summary-expense' : 'detail-summary-income',
            visualFeedback === 'confirm' ? 'ui-feedback-confirm' : '',
            visualFeedback === 'danger' ? 'ui-feedback-danger' : ''
          ]"
        >
          <div class="detail-summary-main">
            <div class="detail-summary-copy">
              <div class="detail-summary-kickers">
                <span>{{ detailTypeText }}</span>
                <span>{{ detailChannelText }}</span>
              </div>
              <h1 class="detail-summary-title">{{ transactionTitle(record) }}</h1>
              <p class="detail-summary-meta">
                <van-icon name="clock-o" />
                <span>{{ displayDateTime(record.occurredAt) }}</span>
              </p>
            </div>
            <div :class="['detail-summary-amount', record.type === 'EXPENSE' ? 'expense' : 'income']">
              {{ record.type === 'EXPENSE' ? '-' : '+' }}¥{{ money(record.amount) }}
            </div>
          </div>
          <div class="detail-summary-chips">
            <span v-for="chip in detailSummaryChips" :key="chip">{{ chip }}</span>
          </div>
        </section>

        <section class="section panel detail-ledger-panel">
          <div class="detail-panel-heading">
            <div>
              <span>账单脉络</span>
              <p>按记录信息顺序阅读</p>
            </div>
          </div>
          <div class="detail-ledger-list">
            <div v-for="item in detailLedgerItems" :key="item.key" class="detail-ledger-item">
              <div class="detail-ledger-icon">
                <van-icon :name="item.icon" />
              </div>
              <div class="detail-ledger-content">
                <div class="detail-ledger-label">{{ item.label }}</div>
                <div class="detail-ledger-value">{{ item.value }}</div>
                <div v-if="item.description" class="detail-ledger-description">{{ item.description }}</div>
              </div>
            </div>
          </div>
        </section>

        <section v-if="recordImages.length" class="section panel detail-images-panel">
          <div class="detail-panel-heading">
            <div>
              <span>凭证图片</span>
              <p>{{ detailImageCountText }}，点击预览</p>
            </div>
          </div>
          <div v-if="imageLoading || imageLoadFailed" class="detail-image-status">
            <van-loading v-if="imageLoading" size="16px">正在加载凭证图片</van-loading>
            <span v-else>部分凭证图片加载失败</span>
          </div>
          <div class="detail-image-grid">
            <button
              v-for="(image, index) in recordImages"
              :key="image.id"
              type="button"
              class="detail-image-thumb"
              @click="previewRecordImage(index)"
            >
              <img v-if="imagePreviewUrls[image.id]" :src="imagePreviewUrls[image.id]" :alt="image.originalFilename" />
              <van-icon v-else name="photo-o" />
            </button>
          </div>
        </section>

        <section :class="['section', 'panel', 'detail-actions', visualFeedback === 'selection' ? 'ui-feedback-selection' : '']">
          <div class="detail-panel-heading">
            <div>
              <span>操作</span>
              <p>编辑、复制或管理这笔记录</p>
            </div>
          </div>
          <div class="detail-main-actions">
            <van-button class="detail-action-button primary" block round type="primary" icon="edit" :loading="optionsLoading" @click="startEdit">
              编辑记录
            </van-button>
            <van-button class="detail-action-button" block round plain type="primary" icon="description-o" :loading="copying" @click="copyRecord">
              复制为今日
            </van-button>
            <van-button class="detail-action-button" block round plain type="primary" icon="replay" @click="createRecurringRule">
              设为周期
            </van-button>
            <van-button class="detail-action-button danger" block plain type="danger" icon="delete-o" :loading="deleting" @click="removeRecord">
              删除记录
            </van-button>
          </div>
        </section>
```

- [ ] **Step 2: Run type-check**

Run:

```bash
cd frontend
npm run type-check
```

Expected: PASS. If it fails on missing class names, that is a CSS issue and should not affect TypeScript; fix only TypeScript errors here.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/TransactionDetailView.vue
git commit -m "重做记录详情查看态结构"
```

## Task 4: Rebuild the Edit-State Template Around the Same Ledger Order

**Files:**
- Modify: `frontend/src/views/TransactionDetailView.vue`

- [ ] **Step 1: Replace the edit entry header and field grouping**

Inside `<van-form v-else-if="record" class="detail-edit-form" @submit="submit">`, keep the existing `BottomSheet` blocks and `FormActionBar`, but replace the first two edit sections with:

```vue
        <section :class="['section', 'panel', 'detail-edit-summary', visualFeedback === 'warning' ? 'ui-feedback-warning' : '']">
          <div class="detail-edit-heading">
            <div>
              <span class="quick-kicker">EDIT ENTRY</span>
              <strong>{{ form.type === 'EXPENSE' ? '编辑支出' : '编辑收入' }}</strong>
            </div>
            <van-radio-group
              v-model="form.type"
              :class="['quick-type-switch', { 'is-right': form.type === 'INCOME' }]"
              direction="horizontal"
              @change="syncCategoryForType"
            >
              <van-radio name="EXPENSE">支出</van-radio>
              <van-radio name="INCOME">收入</van-radio>
            </van-radio-group>
          </div>

          <van-cell-group inset class="quick-cell-group quick-primary-group detail-edit-primary">
            <van-field
              v-model="form.amount"
              class="quick-amount-field"
              label="金额"
              type="text"
              inputmode="decimal"
              placeholder="0.00"
              required
              :style="{ '--quick-amount-input-width': amountInputWidth }"
            />
            <van-field v-model="form.itemName" label="事项" placeholder="如冰棍、工资、泳镜" />
          </van-cell-group>
        </section>

        <section class="section panel detail-edit-ledger">
          <div class="detail-panel-heading">
            <div>
              <span>账单脉络</span>
              <p>与详情页保持同一顺序</p>
            </div>
          </div>

          <div class="detail-edit-ledger-list">
            <ModernDateField v-model="form.occurredAt" mode="datetime" label="时间" title="选择发生时间" required />

            <div class="minimal-block">
              <div class="minimal-block-header">
                <span>分类</span>
                <button type="button" @click="openCategoryPopup">更多</button>
              </div>
              <div ref="categoryChipGridRef" class="quick-chip-grid">
                <button
                  v-for="item in visibleQuickCategoryCandidates"
                  :key="item.id"
                  type="button"
                  :data-option-id="item.id"
                  :class="['quick-chip', { active: form.categoryId === item.id }]"
                  @click="selectCategory(item.id)"
                >
                  <van-icon :name="item.icon || 'records-o'" />
                  <span>{{ item.name }}</span>
                </button>
              </div>
            </div>

            <div class="minimal-block">
              <div class="minimal-block-header">
                <span>支付方式</span>
                <button type="button" @click="openPaymentPopup">更多</button>
              </div>
              <div ref="paymentChipGridRef" class="quick-chip-grid compact">
                <button
                  v-for="item in visibleQuickPaymentCandidates"
                  :key="item.id"
                  type="button"
                  :data-option-id="item.id"
                  :class="['quick-chip', { active: form.paymentMethodId === item.id }]"
                  @click="selectPaymentMethod(item.id)"
                >
                  <van-icon :name="item.icon || 'balance-o'" />
                  <span>{{ item.name }}</span>
                </button>
              </div>
            </div>

            <div class="minimal-row">
              <div class="minimal-row-title">
                <span>渠道</span>
              </div>
              <van-radio-group
                v-model="form.channel"
                :class="['quick-channel-switch', { 'is-right': form.channel === 'OFFLINE' }]"
                direction="horizontal"
              >
                <van-radio name="ONLINE">线上</van-radio>
                <van-radio name="OFFLINE">线下</van-radio>
              </van-radio-group>
            </div>

            <div class="channel-content-switch">
              <Transition :name="form.channel === 'OFFLINE' ? 'channel-slide-left' : 'channel-slide-right'">
                <van-field
                  v-if="form.channel === 'ONLINE'"
                  key="online-app"
                  v-model="form.onlineApp"
                  class="minimal-place-block detail-inline-field"
                  label="线上平台"
                  :placeholder="form.type === 'EXPENSE' ? '如淘宝、美团、京东' : '可选，如银行、公司系统'"
                  :required="form.type === 'EXPENSE'"
                />
                <AmapPlaceField v-else key="offline-place" v-model="form.offlinePlace" class="minimal-place-block" label="线下地点" required />
              </Transition>
            </div>

            <van-cell-group inset class="quick-cell-group detail-note-group">
              <van-field v-model="form.note" label="备注" placeholder="可选" />
            </van-cell-group>
          </div>
        </section>
```

- [ ] **Step 2: Move image management below the edit ledger**

Keep the existing image upload markup but wrap it in a section after `detail-edit-ledger`:

```vue
        <section class="section panel detail-edit-images">
          <div class="detail-panel-heading">
            <div>
              <span>凭证图片</span>
              <p>{{ totalImageCount }} / {{ MAX_TRANSACTION_IMAGES }}</p>
            </div>
          </div>
          <div class="quick-image-upload detail-edit-image-upload">
            <div v-if="imageLoading || imageLoadFailed" class="detail-image-status detail-edit-image-status">
              <van-loading v-if="imageLoading" size="16px">正在加载已有凭证</van-loading>
              <span v-else>部分已有凭证加载失败</span>
            </div>
            <div v-if="recordImages.length" class="detail-image-grid detail-edit-image-grid">
              <div v-for="(image, index) in recordImages" :key="image.id" class="detail-image-manage">
                <button type="button" class="detail-image-thumb" @click="previewRecordImage(index)">
                  <img v-if="imagePreviewUrls[image.id]" :src="imagePreviewUrls[image.id]" :alt="image.originalFilename" />
                  <van-icon v-else name="photo-o" />
                </button>
                <button
                  type="button"
                  class="detail-image-delete"
                  :disabled="imageDeletingId === image.id"
                  :aria-label="`删除${image.originalFilename}`"
                  @click="deleteRecordImage(image.id)"
                >
                  <van-loading v-if="imageDeletingId === image.id" size="14" />
                  <van-icon v-else name="cross" />
                </button>
              </div>
            </div>
            <van-uploader
              v-if="remainingImageSlots > 0"
              v-model="imageFiles"
              multiple
              result-type="file"
              :accept="TRANSACTION_IMAGE_ACCEPT"
              upload-icon="photograph"
              upload-text="上传"
              :max-count="remainingImageSlots"
              :max-size="MAX_TRANSACTION_IMAGE_SIZE"
              :before-read="beforeReadImage"
              @oversize="handleImageOversize"
            />
          </div>
        </section>
```

- [ ] **Step 3: Remove the old duplicate edit sections**

Remove the old `detail-edit-entry quick-entry-panel`, `quick-image-upload` inside it, `advanced-options`, and `quick-extra-panel` section. Keep both `BottomSheet` blocks and `FormActionBar` unchanged.

- [ ] **Step 4: Run type-check**

Run:

```bash
cd frontend
npm run type-check
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/TransactionDetailView.vue
git commit -m "重做记录详情编辑态结构"
```

## Task 5: Replace Detail Page Scoped Styles

**Files:**
- Modify: `frontend/src/views/TransactionDetailView.vue`

- [ ] **Step 1: Remove obsolete read-state styles**

In the `<style scoped>` block, remove style rules that target the old read-state classes:

```css
.detail-hero
.detail-hero-expense
.detail-hero-income
.detail-hero-top
.detail-type-pill
.detail-time
.detail-amount
.detail-title
.detail-tags
.detail-info-panel
.detail-section-title
.detail-info-list
.detail-info-row
.detail-info-label
.detail-info-value
.detail-note
```

Keep reusable edit and image rules that still apply, then adjust names in the next step.

- [ ] **Step 2: Add new read-state styles**

Add these styles near the top of the scoped block:

```css
.detail-page .page-content {
  gap: var(--space-12);
}

.detail-summary {
  display: grid;
  gap: var(--space-12);
  overflow: hidden;
  border-radius: var(--radius-floating);
  padding: var(--space-16);
  box-shadow: var(--shadow-md);
}

.detail-summary-expense {
  background:
    radial-gradient(circle at 88% 8%, rgba(var(--expense-rgb), 0.2), transparent 34%),
    var(--card-bg);
}

.detail-summary-income {
  background:
    radial-gradient(circle at 88% 8%, rgba(var(--income-rgb), 0.2), transparent 34%),
    var(--card-bg);
}

.detail-summary-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-12);
  align-items: start;
}

.detail-summary-copy {
  min-width: 0;
}

.detail-summary-kickers,
.detail-summary-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
}

.detail-summary-kickers span,
.detail-summary-chips span {
  max-width: 100%;
  min-height: var(--space-24);
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  border-radius: var(--radius-pill);
  padding: var(--space-3) var(--space-8);
  background: var(--primary-soft);
  color: var(--text-main);
  font-size: var(--font-size-caption);
  font-weight: 700;
  line-height: var(--line-height-caption);
  overflow-wrap: anywhere;
}

.detail-summary-kickers span {
  color: var(--primary);
}

.detail-summary-title {
  margin: var(--space-10) 0 var(--space-0);
  overflow-wrap: anywhere;
  color: var(--text-main);
  font-size: var(--font-size-panel-title);
  font-weight: 780;
  line-height: var(--line-height-panel-title);
}

.detail-summary-meta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
  margin: var(--space-5) 0 var(--space-0);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  font-weight: 650;
  line-height: var(--line-height-caption);
}

.detail-summary-amount {
  max-width: 100%;
  font-size: var(--font-size-amount-large);
  font-weight: 850;
  line-height: var(--line-height-amount-large);
  text-align: right;
  white-space: nowrap;
}

.detail-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-10);
  margin-bottom: var(--space-12);
}

.detail-panel-heading span {
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 760;
  line-height: var(--line-height-body-strong);
}

.detail-panel-heading p {
  margin: var(--space-2) 0 var(--space-0);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-ledger-panel {
  padding: var(--space-14);
}

.detail-ledger-list {
  display: grid;
}

.detail-ledger-item {
  position: relative;
  display: grid;
  grid-template-columns: var(--space-34) minmax(0, 1fr);
  gap: var(--space-10);
  min-width: 0;
  padding-bottom: var(--space-14);
}

.detail-ledger-item:not(:last-child)::before {
  position: absolute;
  top: var(--space-38);
  bottom: var(--space-4);
  left: 16px;
  width: 1px;
  background: rgba(var(--theme-border-warm-rgb), 0.18);
  content: '';
}

.detail-ledger-item:last-child {
  padding-bottom: var(--space-0);
}

.detail-ledger-icon {
  display: grid;
  width: var(--space-34);
  height: var(--space-34);
  place-items: center;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.16);
  border-radius: var(--radius-card);
  background: var(--primary-soft);
  color: var(--primary);
}

.detail-ledger-icon :deep(.van-icon) {
  font-size: var(--icon-size-md);
}

.detail-ledger-content {
  min-width: 0;
}

.detail-ledger-label {
  color: var(--text-muted);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-ledger-value {
  margin-top: var(--space-2);
  overflow-wrap: anywhere;
  color: var(--text-main);
  font-size: var(--font-size-body-strong);
  font-weight: 720;
  line-height: var(--line-height-body-strong);
  white-space: pre-wrap;
}

.detail-ledger-description {
  margin-top: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}
```

- [ ] **Step 3: Add edit-state layout styles**

Add:

```css
.detail-edit-form {
  display: grid;
  gap: var(--space-12);
  margin: 0;
}

.detail-edit-summary,
.detail-edit-ledger,
.detail-edit-images {
  padding: var(--space-14);
}

.detail-edit-summary {
  display: grid;
  gap: var(--space-12);
  background:
    radial-gradient(circle at 88% 4%, rgba(var(--theme-primary-glow-rgb), 0.2), transparent 36%),
    var(--card-bg);
}

.detail-edit-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-12);
  align-items: center;
}

.detail-edit-heading strong {
  display: block;
  margin-top: var(--space-3);
  font-size: var(--font-size-panel-title);
  line-height: var(--line-height-panel-title);
}

.detail-edit-primary {
  margin: 0;
}

.detail-edit-ledger-list {
  display: grid;
  gap: var(--space-12);
}

.detail-note-group {
  margin: 0;
}

.detail-edit-images .quick-image-upload {
  padding: 0;
  border-top: 0;
}
```

- [ ] **Step 4: Keep and normalize image/action styles**

Ensure the file contains these image and action rules, replacing older duplicate definitions if needed:

```css
.detail-images-panel {
  padding: var(--space-14);
}

.detail-image-status {
  display: flex;
  align-items: center;
  min-height: var(--space-28);
  padding: var(--space-0) var(--space-0) var(--space-10);
  color: var(--text-secondary);
  font-size: var(--font-size-caption);
  line-height: var(--line-height-caption);
}

.detail-edit-image-status {
  padding: 0;
}

.detail-image-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-10);
}

.detail-image-thumb {
  display: grid;
  width: 100%;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(var(--theme-border-warm-rgb), 0.2);
  border-radius: var(--radius-card);
  background: rgba(var(--theme-border-warm-rgb), 0.08);
  color: var(--text-secondary);
}

.detail-image-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-image-thumb :deep(.van-icon) {
  font-size: var(--icon-size-lg);
}

.detail-image-manage {
  position: relative;
  min-width: 0;
}

.detail-image-delete {
  position: absolute;
  top: var(--space-4);
  right: var(--space-4);
  display: grid;
  width: var(--space-28);
  height: var(--space-28);
  place-items: center;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--glass-strong-bg);
  color: var(--text-main);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
}

.detail-actions {
  display: grid;
  gap: var(--space-10);
  padding: var(--space-14);
}

.detail-main-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-10);
}

.detail-action-button {
  min-height: 46px;
  border-radius: var(--radius-card);
}

.detail-action-button.danger {
  border-color: rgba(var(--expense-rgb), 0.22);
  background: rgba(var(--expense-rgb), 0.08);
}
```

- [ ] **Step 5: Add narrow-screen rules**

Ensure the scoped block has:

```css
@media (max-width: 360px) {
  .detail-summary-main,
  .detail-edit-heading,
  .detail-main-actions,
  .minimal-row {
    grid-template-columns: 1fr;
  }

  .detail-summary-amount {
    text-align: left;
    white-space: normal;
  }

  .quick-type-switch,
  .quick-channel-switch {
    width: 100%;
  }
}
```

- [ ] **Step 6: Run UI check and type-check**

Run:

```bash
cd frontend
npm run check:ui
npm run type-check
```

Expected: both PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/views/TransactionDetailView.vue
git commit -m "完善记录详情页样式"
```

## Task 6: Final Build Verification

**Files:**
- Modify only if verification reveals a defect.

- [ ] **Step 1: Run helper test**

Run:

```bash
cd frontend
node tests/transactionDetailPresentation.test.ts
```

Expected: PASS with no output.

- [ ] **Step 2: Run production build**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS. This includes `npm run check:ui`, `vue-tsc --noEmit`, and `vite build`.

- [ ] **Step 3: Manual browser smoke check**

Start the dev server:

```bash
cd frontend
npm run dev
```

Open a record detail page and check:

- Read state shows summary, ledger, images only when present, and bottom actions.
- Edit state shows amount and item first, then time, category, payment method, channel/place, note, images, and save bar.
- Income and expense colors differ.
- Online records show “线上平台”; offline records show “线下地点”.
- `2026-06-20T12:24:30` displays as `2026年06月20日 12:24`.
- At 320px width, title, amount, chips, and ledger values do not overflow.

- [ ] **Step 4: Final commit if verification fixes were needed**

If Task 6 required code changes, run:

```bash
git add frontend/src/utils/transactionDetailPresentation.ts frontend/tests/transactionDetailPresentation.test.ts frontend/src/views/TransactionDetailView.vue
git commit -m "修复记录详情页验证问题"
```

If no code changed during Task 6, do not create an empty commit.
