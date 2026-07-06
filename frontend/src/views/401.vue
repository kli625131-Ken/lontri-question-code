<template>
  <div class="unauthorized page-shell">
    <section class="section-card unauthorized-card">
      <div class="code">401</div>
      <div class="page-title">当前账号没有访问该页面的权限</div>
      <p class="page-subtitle">如需跨项目检索或维护其他项目，请联系管理员补充项目授权或全局检索权限。</p>
      <el-button type="primary" @click="$router.push('/dashboard')">返回仪表盘</el-button>
    </section>
  </div>
</template>

<style scoped>
.unauthorized {
  min-height: 80vh;
  display: grid;
  place-items: center;
}

.unauthorized-card {
  max-width: 620px;
  text-align: center;
}

.code {
  font-size: 76px;
  font-weight: 900;
  color: var(--primary);
}
</style>
