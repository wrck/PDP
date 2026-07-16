<template>
  <main class="home">
    <section>
      <p class="eyebrow">PDP · Project Delivery Platform</p>
      <h1>企业级项目交付管理平台</h1>
      <p>统一管理工作空间、组织权限和项目交付闭环。</p>
      <nav class="home__entries">
        <RouterLink class="home__entry" to="/workspaces">进入工作空间治理</RouterLink>
        <RouterLink class="home__entry home__entry--secondary" to="/admin/workflow">
          管理平台工作流
        </RouterLink>
      </nav>
    </section>
  </main>
</template>

<style scoped>
.home__entry {
  display: inline-flex;
  margin-top: 24px;
  padding: 12px 18px;
  border-radius: 8px;
  color: white;
  background: var(--pdp-color-brand);
  text-decoration: none;
}

.home__entries {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.home__entry--secondary {
  color: var(--pdp-color-brand-strong);
  background: color-mix(in srgb, var(--pdp-color-brand) 10%, white);
}
</style>
