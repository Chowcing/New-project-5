import { strict as assert } from 'node:assert'
import { transactionEditOnlinePlatformFields } from '../src/utils/transactionEditPayload.ts'
import type { OnlinePlatform } from '../src/types.ts'

const platforms: OnlinePlatform[] = [
  { id: 3, name: '美团', icon: 'shop-o', sortOrder: 10, pinned: true },
  { id: 5, name: '京东', icon: 'cart-o', sortOrder: 20, pinned: false }
]

assert.deepEqual(
  transactionEditOnlinePlatformFields(
    {
      channel: 'ONLINE',
      onlineApp: '旧平台',
      onlinePlatformId: 3
    },
    platforms
  ),
  {
    onlineApp: '美团',
    onlinePlatformId: 3
  }
)

assert.deepEqual(
  transactionEditOnlinePlatformFields(
    {
      channel: 'ONLINE',
      onlineApp: ' 手写平台 ',
      onlinePlatformId: undefined
    },
    platforms
  ),
  {
    onlineApp: '手写平台',
    onlinePlatformId: undefined
  }
)

assert.deepEqual(
  transactionEditOnlinePlatformFields(
    {
      channel: 'OFFLINE',
      onlineApp: '美团',
      onlinePlatformId: 3
    },
    platforms
  ),
  {
    onlineApp: undefined,
    onlinePlatformId: undefined
  }
)
