<template>
  <router-view />
</template>

<style>
/* 基礎重置 */
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  width: 100%;
  background-color: #0a0a0c; /* 暗黑背景底色 */
}

/* 全局自定義滾動條 (高級感) */
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-thumb { background: #333; border-radius: 10px; }
::-webkit-scrollbar-thumb:hover { background: #444; }
</style>