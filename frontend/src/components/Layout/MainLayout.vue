<template>
  <div class="layout-shell">
    <aside class="layout-sidebar">
      <Sidebar />
    </aside>
    <div class="layout-main">
      <Header />
      <main class="layout-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import Header from './Header.vue'
import Sidebar from './Sidebar.vue'
</script>

<style scoped>
.layout-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  background: var(--bg-page);
}

.layout-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  border-right: 1px solid var(--line-soft);
  background: #fff;
}

.layout-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.layout-content {
  min-width: 0;
  padding: 20px 24px 28px;
}

@media (max-width: 1080px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .layout-sidebar {
    position: static;
    height: auto;
  }

  .layout-content {
    padding: 16px;
  }
}
</style>
