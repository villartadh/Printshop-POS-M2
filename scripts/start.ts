process.env.NODE_ENV = 'production';
const {startServer} = await import('../server.ts');
const stop = await startServer();
process.once('SIGINT', () => void stop());
process.once('SIGTERM', () => void stop());
