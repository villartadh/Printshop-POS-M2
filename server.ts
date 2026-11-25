import 'dotenv/config';
import express from 'express';
import fs from 'fs';
import http from 'http';
import https from 'node:https';
import {pathToFileURL} from 'node:url';
import {isIP} from 'node:net';
import type {TLSSocket} from 'node:tls';
import path from 'path';

import { initDatabase } from './src/server/db.ts';
import { allowedOrigin, allowedHttpsOrigin } from './src/shared/securityPolicy.ts';
import { apiRouter } from './src/server/routes.ts';

export async function startServer() {
  const isProd = process.env.NODE_ENV === 'production';
  const port = isProd ? Number(process.env.PORT) || 3000 : 3000;
  const host = process.env.HOST || '127.0.0.1';
  if (!['127.0.0.1', '::1', 'localhost'].includes(host)) throw new Error('The HTTP listener requires loopback.');
  const {POS_HTTPS_ORIGIN, POS_HTTPS_BIND, POS_HTTPS_CERT, POS_HTTPS_KEY} = process.env;
  const useHttps = [POS_HTTPS_ORIGIN, POS_HTTPS_BIND, POS_HTTPS_CERT, POS_HTTPS_KEY].some(value => value !== undefined);
  let network: {origin: URL; bind: string; port: number; cert: Buffer; key: Buffer} | undefined;
  if (useHttps) {
    if (!isProd || !POS_HTTPS_ORIGIN || !POS_HTTPS_BIND || !POS_HTTPS_CERT || !POS_HTTPS_KEY)
      throw new Error('HTTPS requires production mode, an origin, a bind address, a certificate and a private key.');
    const origin = new URL(POS_HTTPS_ORIGIN);
    if (origin.protocol !== 'https:' || origin.origin !== POS_HTTPS_ORIGIN || !isIP(POS_HTTPS_BIND) ||
        ['0.0.0.0', '::'].includes(POS_HTTPS_BIND)) throw new Error('Set a canonical HTTPS origin and a specific bind IP address.');
    const tlsPort = Number(origin.port || 443);
    if (tlsPort < 1 || tlsPort === port) throw new Error('Choose a different, nonzero HTTPS port.');
    network = {origin,bind:POS_HTTPS_BIND,port:tlsPort,cert:fs.readFileSync(POS_HTTPS_CERT),key:fs.readFileSync(POS_HTTPS_KEY)};
  }
  initDatabase();

  const app = express();
  const httpServer = http.createServer(app);
  app.disable('x-powered-by');
  app.use((req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('X-Frame-Options', 'DENY');
    const encrypted = (req.socket as TLSSocket).encrypted === true;
    const allowed = encrypted
      ? network && allowedHttpsOrigin(network.origin, req.headers.host, req.headers.origin)
      : allowedOrigin(req.headers.host, req.headers.origin);
    if (!allowed) return res.status(403).json({ error: 'Same-origin access required.' });
    res.locals.networkAccess = encrypted;
    next();
  });
  app.use('/api', (_req, res, next) => { res.setHeader('Cache-Control', 'no-store'); next(); });
  app.use(express.json({ limit: '5mb' }));

  app.get('/api/health', (_req, res) => {
    res.json({ status: 'ok', time: new Date().toISOString(), ...(process.env.LDC_INSTANCE ? { instance: process.env.LDC_INSTANCE } : {}) });
  });

  app.use('/api', apiRouter);
  app.use('/api', (_req, res) => res.status(404).json({ error: 'API endpoint not found.' }));
  app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    console.error("API request failed", Number(err.statusCode || err.status) || 500);
    const status = Number(err.statusCode || err.status);
    res.status(status >= 400 && status < 500 ? status : 500).json({ error: status >= 400 && status < 500 ? err.message : 'Server operation failed. Check server logs.' });
  });

  const distPath = path.resolve(process.cwd(), 'dist');

  if (isProd && !fs.existsSync(path.join(distPath, 'index.html'))) throw new Error('Production build missing. Run npm run build first.');
  if (!isProd) {
    const { createServer: createViteServer } = await import('vite');
    const hmrDisabled = process.env.DISABLE_HMR === 'true';
    const vite = await createViteServer({
      plugins: hmrDisabled
        ? [
            {
              name: 'disable-vite-client-ws-when-hmr-off',
              enforce: 'post',
              transform(code: string, id: string) {
                if (
                  id.endsWith('/dist/client/client.mjs') ||
                  id.endsWith('\\dist\\client\\client.mjs')
                ) {
                  const startMarker = 'const transport = normalizeModuleRunnerTransport((() => {';
                  const endMarker = 'let willUnload = false;';
                  const startIdx = code.indexOf(startMarker);
                  const endIdx = code.indexOf(endMarker);
                  if (startIdx !== -1 && endIdx > startIdx) {
                    const noopTransport =
                      'const transport = normalizeModuleRunnerTransport({ connect() {}, disconnect() {}, send() {} });';
                    return {
                      code: code.slice(0, startIdx) + noopTransport + code.slice(endIdx),
                      map: null,
                    };
                  }
                }
                return undefined;
              },
            },
          ]
        : [],
      server: {
        middlewareMode: true,
        hmr: hmrDisabled ? false : { server: httpServer },
        ws: hmrDisabled ? false : { server: httpServer },
        watch: hmrDisabled ? null : undefined,
      },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  const httpsServer = network && https.createServer({cert:network.cert,key:network.key,minVersion:'TLSv1.2'},app);
  const listen = (server: http.Server, port: number, host: string) =>
    new Promise<void>((resolve,reject) => {
      server.once('error',reject);
      server.listen({port,host,exclusive:true},() => {
        server.off('error',reject);
        resolve();
      });
    });
  const close = (server: http.Server) =>
    new Promise<void>(resolve => server.close(() => resolve()));
  try {
    await listen(httpServer,port,host);
    if (network && httpsServer) await listen(httpsServer,network.port,network.bind);
  } catch (error) {
    await close(httpServer);
    throw error;
  }
  console.log(`Printshop POS server running on loopback port ${port}`);
  if (network) console.log(`Printshop POS HTTPS enabled at ${network.origin.origin}`);
  return async () => {
    const servers = httpsServer ? [httpServer,httpsServer] : [httpServer];
    const deadline = setTimeout(() => servers.forEach(server => server.closeAllConnections()),10000);
    try { await Promise.all(servers.map(close)); }
    finally { clearTimeout(deadline); }
  };
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href===import.meta.url) {
  await startServer().catch(error => {
    console.error('Failed to start Printshop POS server:',error.message);
    process.exitCode=1;
  });
}
