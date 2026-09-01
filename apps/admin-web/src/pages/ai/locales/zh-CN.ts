export default {
  loading: '加载中…',
  empty: '暂无数据',
  error: '加载失败',
  retry: '重试',
  noSites: '暂无站点，请先创建站点。',
  siteLabel: '选择站点',
  settings: {
    title: 'AI 运行配置',
    description: '说明当前部署如何选择模型。没有 Java AI Config 表可编辑。',
    body: '模型由 agent-services 进程环境变量 AI_MODEL 决定（Pydantic AI spec，无默认值）。',
    envHint: '站点级 Prompt / 温度在「Prompt 配置」中维护；Java 会在调用 Python 时注入，前端不能直传 systemPrompt。',
    blockedHint: '未配置 AI_MODEL 时预览与站点聊天会诚实失败（503 / BLOCKED），不会伪装成功。',
  },
  prompts: {
    title: 'Prompt 配置',
    description: '按站点维护系统 Prompt 与温度。保存后由 Java 注入 Python runtime。',
  },
  knowledgeBases: {
    title: '知识库',
    description: '按站点维护知识库文档（Java 为真相源；索引同步到 Python 派生库）。',
  },
  conversations: {
    title: '对话记录',
    description: '只读查看站点用户与 AI 的会话与消息（会话真相在 Java）。',
  },
} as const
