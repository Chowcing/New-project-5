import { strict as assert } from 'node:assert'
import { transactionEditOnlinePlatformFields } from '../src/utils/transactionEditPayload.ts'

assert.deepEqual(
  transactionEditOnlinePlatformFields({
    channel: 'ONLINE',
    onlineApp: '历史美团',
    onlinePlatformId: 3
  }),
  {
    onlineApp: '历史美团',
    onlinePlatformId: 3
  }
)

assert.deepEqual(
  transactionEditOnlinePlatformFields({
    channel: 'ONLINE',
    onlineApp: '京东',
    onlinePlatformId: 5
  }),
  {
    onlineApp: '京东',
    onlinePlatformId: 5
  }
)

assert.deepEqual(
  transactionEditOnlinePlatformFields({
    channel: 'ONLINE',
    onlineApp: ' 手写平台 ',
    onlinePlatformId: undefined
  }),
  {
    onlineApp: '手写平台',
    onlinePlatformId: undefined
  }
)

assert.deepEqual(
  transactionEditOnlinePlatformFields({
    channel: 'OFFLINE',
    onlineApp: '美团',
    onlinePlatformId: 3
  }),
  {
    onlineApp: undefined,
    onlinePlatformId: undefined
  }
)
