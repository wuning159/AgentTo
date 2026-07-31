<script setup lang="ts">
defineProps<{ eyebrow: string; title: string; description: string }>()
</script>

<template>
  <header class="page-heading">
    <div>
      <p class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p class="page-heading__description">{{ description }}</p>
    </div>
    <div class="page-heading__actions"><slot /></div>
  </header>
</template>
