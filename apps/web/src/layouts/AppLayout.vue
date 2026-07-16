<script setup lang="ts">
defineProps<{
  title: string
  navigationLabel?: string
}>()
</script>

<template>
  <div class="app-layout">
    <aside class="app-layout__navigation" :aria-label="navigationLabel ?? '主导航'">
      <slot name="navigation" />
    </aside>
    <div class="app-layout__main">
      <header class="app-layout__header">
        <div>
          <slot name="eyebrow" />
          <h1>{{ title }}</h1>
        </div>
        <div class="app-layout__actions"><slot name="actions" /></div>
      </header>
      <main class="app-layout__content">
        <slot />
      </main>
    </div>
  </div>
</template>
