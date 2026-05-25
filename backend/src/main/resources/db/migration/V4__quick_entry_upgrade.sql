ALTER TABLE categories
  ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0 AFTER sort_order;

ALTER TABLE payment_methods
  ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0 AFTER sort_order;

CREATE TABLE IF NOT EXISTS online_platforms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  icon VARCHAR(32) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  pinned TINYINT(1) NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_name VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN name ELSE NULL END) STORED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_online_platforms_user_active_name (user_id, active_name),
  INDEX idx_online_platforms_user_id (user_id, deleted),
  CONSTRAINT fk_online_platforms_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE transactions
  MODIFY item_name VARCHAR(64) NULL,
  ADD COLUMN online_platform_id BIGINT NULL AFTER online_app,
  ADD INDEX idx_transactions_online_platform (online_platform_id),
  ADD CONSTRAINT fk_transactions_online_platform FOREIGN KEY (online_platform_id) REFERENCES online_platforms(id);

INSERT INTO categories (user_id, name, type, icon, color, sort_order, pinned)
SELECT u.id, seed.name, seed.type, seed.icon, seed.color, seed.sort_order, 0
FROM users u
JOIN (
  SELECT '餐饮' name, 'EXPENSE' type, 'shop-o' icon, '#ee6a5c' color, 10 sort_order UNION ALL
  SELECT '交通', 'EXPENSE', 'logistics', '#4d8cff', 20 UNION ALL
  SELECT '购物', 'EXPENSE', 'cart-o', '#f0a23a', 30 UNION ALL
  SELECT '买菜', 'EXPENSE', 'bag-o', '#2f7d68', 40 UNION ALL
  SELECT '外卖', 'EXPENSE', 'shop-o', '#f97316', 50 UNION ALL
  SELECT '零食饮料', 'EXPENSE', 'cart-circle-o', '#ec4899', 60 UNION ALL
  SELECT '居住', 'EXPENSE', 'home-o', '#8b5cf6', 70 UNION ALL
  SELECT '水电燃气', 'EXPENSE', 'fire-o', '#f59e0b', 80 UNION ALL
  SELECT '通讯网络', 'EXPENSE', 'phone-o', '#3b82f6', 90 UNION ALL
  SELECT '娱乐', 'EXPENSE', 'music-o', '#d85f8a', 100 UNION ALL
  SELECT '社交', 'EXPENSE', 'friends-o', '#06b6d4', 110 UNION ALL
  SELECT '人情', 'EXPENSE', 'gift-o', '#ec4899', 120 UNION ALL
  SELECT '医疗', 'EXPENSE', 'shield-o', '#e25555', 130 UNION ALL
  SELECT '教育', 'EXPENSE', 'bookmark-o', '#64748b', 140 UNION ALL
  SELECT '旅行', 'EXPENSE', 'hotel-o', '#14b8a6', 150 UNION ALL
  SELECT '宠物', 'EXPENSE', 'smile-o', '#a855f7', 160 UNION ALL
  SELECT '育儿', 'EXPENSE', 'like-o', '#fb7185', 170 UNION ALL
  SELECT '数码', 'EXPENSE', 'desktop-o', '#0ea5e9', 180 UNION ALL
  SELECT '服饰', 'EXPENSE', 'bag-o', '#db2777', 190 UNION ALL
  SELECT '美妆', 'EXPENSE', 'flower-o', '#f43f5e', 200 UNION ALL
  SELECT '运动健身', 'EXPENSE', 'fire-o', '#16a34a', 210 UNION ALL
  SELECT '金融保险', 'EXPENSE', 'shield-o', '#334155', 220 UNION ALL
  SELECT '会员订阅', 'EXPENSE', 'gem-o', '#7c3aed', 230 UNION ALL
  SELECT '办公学习', 'EXPENSE', 'records-o', '#2563eb', 240 UNION ALL
  SELECT '汽车', 'EXPENSE', 'logistics', '#475569', 250 UNION ALL
  SELECT '家居', 'EXPENSE', 'wap-home-o', '#8b5e34', 260 UNION ALL
  SELECT '烟酒', 'EXPENSE', 'hot-o', '#92400e', 270 UNION ALL
  SELECT '公益捐赠', 'EXPENSE', 'good-job-o', '#059669', 280 UNION ALL
  SELECT '其他', 'EXPENSE', 'records-o', '#64748b', 990
) seed
LEFT JOIN categories existing
  ON existing.user_id = u.id
  AND existing.type = seed.type
  AND existing.name = seed.name
  AND existing.deleted = 0
WHERE existing.id IS NULL;

UPDATE transactions t
JOIN categories oldc ON oldc.id = t.category_id AND oldc.user_id = t.user_id AND oldc.type = 'EXPENSE'
JOIN categories newc
  ON newc.user_id = t.user_id
  AND newc.type = 'EXPENSE'
  AND newc.deleted = 0
  AND newc.name = CASE oldc.name
    WHEN '住房' THEN '居住'
    WHEN '通讯' THEN '通讯网络'
    WHEN '人情礼金' THEN '人情'
    WHEN '其他支出' THEN '其他'
    WHEN '日用' THEN '家居'
  END
SET t.category_id = newc.id
WHERE oldc.name IN ('住房', '通讯', '人情礼金', '其他支出', '日用')
  AND oldc.id <> newc.id;

UPDATE budgets b
JOIN categories oldc ON oldc.id = b.category_id AND oldc.user_id = b.user_id AND oldc.type = 'EXPENSE'
JOIN categories newc
  ON newc.user_id = b.user_id
  AND newc.type = 'EXPENSE'
  AND newc.deleted = 0
  AND newc.name = CASE oldc.name
    WHEN '住房' THEN '居住'
    WHEN '通讯' THEN '通讯网络'
    WHEN '人情礼金' THEN '人情'
    WHEN '其他支出' THEN '其他'
    WHEN '日用' THEN '家居'
  END
SET b.category_id = newc.id
WHERE oldc.name IN ('住房', '通讯', '人情礼金', '其他支出', '日用')
  AND oldc.id <> newc.id;

UPDATE recurring_rules r
JOIN categories oldc ON oldc.id = r.category_id AND oldc.user_id = r.user_id AND oldc.type = 'EXPENSE'
JOIN categories newc
  ON newc.user_id = r.user_id
  AND newc.type = 'EXPENSE'
  AND newc.deleted = 0
  AND newc.name = CASE oldc.name
    WHEN '住房' THEN '居住'
    WHEN '通讯' THEN '通讯网络'
    WHEN '人情礼金' THEN '人情'
    WHEN '其他支出' THEN '其他'
    WHEN '日用' THEN '家居'
  END
SET r.category_id = newc.id,
    r.category_name = newc.name
WHERE oldc.name IN ('住房', '通讯', '人情礼金', '其他支出', '日用')
  AND oldc.id <> newc.id;

UPDATE recurring_rule_runs rr
JOIN categories oldc ON oldc.id = rr.category_id AND oldc.user_id = rr.user_id AND oldc.type = 'EXPENSE'
JOIN categories newc
  ON newc.user_id = rr.user_id
  AND newc.type = 'EXPENSE'
  AND newc.deleted = 0
  AND newc.name = CASE oldc.name
    WHEN '住房' THEN '居住'
    WHEN '通讯' THEN '通讯网络'
    WHEN '人情礼金' THEN '人情'
    WHEN '其他支出' THEN '其他'
    WHEN '日用' THEN '家居'
  END
SET rr.category_id = newc.id
WHERE oldc.name IN ('住房', '通讯', '人情礼金', '其他支出', '日用')
  AND oldc.id <> newc.id;

UPDATE categories oldc
JOIN categories newc
  ON newc.user_id = oldc.user_id
  AND newc.type = oldc.type
  AND newc.deleted = 0
  AND newc.name = CASE oldc.name
    WHEN '住房' THEN '居住'
    WHEN '通讯' THEN '通讯网络'
    WHEN '人情礼金' THEN '人情'
    WHEN '其他支出' THEN '其他'
    WHEN '日用' THEN '家居'
  END
SET oldc.deleted = 1
WHERE oldc.type = 'EXPENSE'
  AND oldc.deleted = 0
  AND oldc.name IN ('住房', '通讯', '人情礼金', '其他支出', '日用')
  AND oldc.id <> newc.id
  AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.category_id = oldc.id AND t.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM budgets b WHERE b.category_id = oldc.id AND b.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM recurring_rules r WHERE r.category_id = oldc.id AND r.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM recurring_rule_runs rr WHERE rr.category_id = oldc.id);

INSERT INTO payment_methods (user_id, name, icon, sort_order, pinned)
SELECT u.id, seed.name, seed.icon, seed.sort_order, 0
FROM users u
JOIN (
  SELECT '微信' name, 'wechat-pay' icon, 10 sort_order UNION ALL
  SELECT '支付宝', 'alipay', 20 UNION ALL
  SELECT '银行卡', 'balance-o', 30 UNION ALL
  SELECT '云闪付', 'ecard-pay', 40 UNION ALL
  SELECT '现金', 'cash-back-record', 50 UNION ALL
  SELECT '数字人民币', 'gold-coin-o', 60
) seed
LEFT JOIN payment_methods existing
  ON existing.user_id = u.id
  AND existing.name = seed.name
  AND existing.deleted = 0
WHERE existing.id IS NULL;

UPDATE transactions t
JOIN payment_methods oldpm ON oldpm.id = t.payment_method_id AND oldpm.user_id = t.user_id
JOIN payment_methods newpm ON newpm.user_id = t.user_id AND newpm.name = '银行卡' AND newpm.deleted = 0
SET t.payment_method_id = newpm.id,
    t.payment_method_name = newpm.name
WHERE oldpm.name IN ('信用卡', '借记卡')
  AND oldpm.id <> newpm.id;

UPDATE recurring_rules r
JOIN payment_methods oldpm ON oldpm.id = r.payment_method_id AND oldpm.user_id = r.user_id
JOIN payment_methods newpm ON newpm.user_id = r.user_id AND newpm.name = '银行卡' AND newpm.deleted = 0
SET r.payment_method_id = newpm.id,
    r.payment_method_name = newpm.name
WHERE oldpm.name IN ('信用卡', '借记卡')
  AND oldpm.id <> newpm.id;

UPDATE recurring_rule_runs rr
JOIN payment_methods oldpm ON oldpm.id = rr.payment_method_id AND oldpm.user_id = rr.user_id
JOIN payment_methods newpm ON newpm.user_id = rr.user_id AND newpm.name = '银行卡' AND newpm.deleted = 0
SET rr.payment_method_id = newpm.id
WHERE oldpm.name IN ('信用卡', '借记卡')
  AND oldpm.id <> newpm.id;

UPDATE payment_methods oldpm
LEFT JOIN recurring_rules active_rules
  ON active_rules.payment_method_id = oldpm.id
  AND active_rules.deleted = 0
SET oldpm.deleted = 1
WHERE oldpm.deleted = 0
  AND oldpm.name IN ('信用卡', '借记卡', '其他')
  AND active_rules.id IS NULL;

INSERT INTO online_platforms (user_id, name, icon, sort_order, pinned)
SELECT u.id, seed.name, seed.icon, seed.sort_order, 0
FROM users u
JOIN (
  SELECT '淘宝' name, 'shop-o' icon, 10 sort_order UNION ALL
  SELECT '天猫', 'cart-o', 20 UNION ALL
  SELECT '京东', 'goods-collect-o', 30 UNION ALL
  SELECT '抖音', 'video-o', 40 UNION ALL
  SELECT '小红书', 'notes-o', 50 UNION ALL
  SELECT '携程旅行', 'hotel-o', 60 UNION ALL
  SELECT '拼多多', 'cart-circle-o', 70 UNION ALL
  SELECT '美团', 'shop-o', 80 UNION ALL
  SELECT '闲鱼', 'exchange', 90 UNION ALL
  SELECT '高德', 'location-o', 100 UNION ALL
  SELECT '滴滴', 'logistics', 110 UNION ALL
  SELECT '铁路12306', 'train-o', 120 UNION ALL
  SELECT '哔哩哔哩', 'tv-o', 130 UNION ALL
  SELECT '微信', 'wechat-pay', 140 UNION ALL
  SELECT '支付宝', 'alipay', 150 UNION ALL
  SELECT '饿了么', 'shop-o', 160 UNION ALL
  SELECT '百度地图', 'location-o', 170
) seed
LEFT JOIN online_platforms existing
  ON existing.user_id = u.id
  AND existing.name = seed.name
  AND existing.deleted = 0
WHERE existing.id IS NULL;

INSERT INTO online_platforms (user_id, name, icon, sort_order, pinned)
SELECT DISTINCT t.user_id, t.online_app, 'apps-o', 900, 0
FROM transactions t
LEFT JOIN online_platforms existing
  ON existing.user_id = t.user_id
  AND existing.name = t.online_app
  AND existing.deleted = 0
WHERE t.channel = 'ONLINE'
  AND t.online_app IS NOT NULL
  AND t.online_app <> ''
  AND existing.id IS NULL;

UPDATE transactions t
JOIN online_platforms p
  ON p.user_id = t.user_id
  AND p.name = t.online_app
  AND p.deleted = 0
SET t.online_platform_id = p.id
WHERE t.channel = 'ONLINE'
  AND t.online_app IS NOT NULL
  AND t.online_app <> ''
  AND t.online_platform_id IS NULL;
