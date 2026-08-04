<script setup lang="ts">
import { ref } from 'vue'
import ModernDateField from '@/components/ModernDateField.vue'

const date = ref('2026-08-04')
const datetime = ref('2026-08-04T09:07')
const month = ref('2026-08')
const year = ref('2026')
const availableDates = ['2026-08-05']
const minDate = new Date(2026, 7, 4)
const maxDate = new Date(2026, 7, 5)
</script>

<template>
  <main>
    <div data-testid="date-field">
      <ModernDateField
        v-model="date"
        mode="date"
        label="日期"
        title="选择日期"
        :available-dates="availableDates"
        :min-date="minDate"
        :max-date="maxDate"
      />
    </div>
    <div data-testid="datetime-field">
      <ModernDateField v-model="datetime" mode="datetime" label="日期时间" title="选择日期时间" />
    </div>
    <div data-testid="month-field">
      <ModernDateField v-model="month" mode="month" label="月份" title="选择月份" />
    </div>
    <div data-testid="year-field">
      <ModernDateField v-model="year" mode="year" label="年份" title="选择年份" />
    </div>
  </main>
</template>
