import crypto from 'crypto';
import fs from 'node:fs';
import path from 'node:path';
import type { NextFunction, Request, Response } from 'express';
import { db, writeAuditLog } from './db.ts';
import type { UserAccount, UserRole } from '../shared/types.ts';

function sessionSecret() {
  if (process.env.SESSION_SECRET) return process.env.SESSION_SECRET;
  const file = path.resolve(process.env.POS_DATA_DIR || 'data', 'session.secret');
  try { return fs.readFileSync(file, 'utf8').trim(); } catch (err: any) { if (err.code !== 'ENOENT') throw err; }
  const secret = crypto.randomBytes(32).toString('hex');
  fs.writeFileSync(file, secret, { mode: 0o600, flag: 'wx' });
  return secret;
}
const TOKEN_SECRET = sessionSecret();

export function hashSecret(value: string): string {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = crypto.scryptSync(value, salt, 64).toString('hex');
  return `${salt}:${derived}`;
}

export function verifySecret(value: string, storedHash: string): boolean {
  if (!storedHash || !storedHash.includes(':')) return false;
  const [salt, keyHex] = storedHash.split(':');
  if (!salt || !keyHex) return false;
  try {
    const derived = crypto.scryptSync(value, salt, 64);
    const keyBuf = Buffer.from(keyHex, 'hex');
    if (derived.length !== keyBuf.length) return false;
    return crypto.timingSafeEqual(derived, keyBuf);
  } catch {
    return false;
  }
}

export function signSessionToken(sessionId: string, userId: string): string {
  const payload = Buffer.from(JSON.stringify({ sid: sessionId, uid: userId })).toString('base64url');
  const sig = crypto.createHmac('sha256', TOKEN_SECRET).update(payload).digest('base64url');
  return `${payload}.${sig}`;
}

export function verifySessionToken(token: string): { sid: string; uid: string } | null {
  if (!token || !token.includes('.')) return null;
  const [payload, sig] = token.split('.');
  if (!payload || !sig || token.split('.').length !== 2 || token.length > 2048) return null;
  const expected = crypto.createHmac('sha256', TOKEN_SECRET).update(payload).digest('base64url');
  try {
    const sigBuf = Buffer.from(sig);
    const expBuf = Buffer.from(expected);
    if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
      return null;
    }
    const parsed = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    if (typeof parsed.sid === 'string' && typeof parsed.uid === 'string') {
      return { sid: parsed.sid, uid: parsed.uid };
    }
    return null;
  } catch {
    return null;
  }
}

interface UserRow {
  id: string;
  username: string;
  full_name: string;
  email: string;
  role: UserRole;
  password_hash: string;
  pin_hash: string;
  is_active: number;
  preferred_landing_module: string | null;
  created_at: string;
}

export function sanitizeUser(row: UserRow): UserAccount {
  return {
    id: row.id,
    username: row.username,
    fullName: row.full_name,
    email: row.email,
    role: row.role,
    isActive: row.is_active === 1,
    hasPin: Boolean(row.pin_hash),
    preferredLandingModule: (row.preferred_landing_module as UserAccount['preferredLandingModule']) || 'pos',
    createdAt: row.created_at,
  };
}

export interface AuthenticatedRequest extends Request {
  authUser?: UserAccount;
  sessionId?: string;
}

export function requireAuth(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  const verified = verifySessionToken(token);
  if (!verified) {
    return res.status(401).json({ error: 'Authentication required. Please sign in.' });
  }

  const session = db
    .prepare('SELECT id, user_id, expires_at FROM sessions WHERE id = ? AND user_id = ?')
    .get(verified.sid, verified.uid) as { id: string; user_id: string; expires_at: string } | undefined;

  if (!session || !(new Date(session.expires_at).getTime() > Date.now())) {
    return res.status(401).json({ error: 'Session expired. Please sign in again.' });
  }

  const userRow = db
    .prepare('SELECT * FROM users WHERE id = ? AND is_active = 1')
    .get(verified.uid) as UserRow | undefined;

  if (!userRow) {
    return res.status(401).json({ error: 'Account inactive or unavailable.' });
  }

  db.prepare('UPDATE sessions SET last_seen_at = ? WHERE id = ?').run(
    new Date().toISOString(),
    session.id,
  );

  req.authUser = sanitizeUser(userRow);
  req.sessionId = session.id;
  next();
}

export function requireRole(allowedRoles: UserRole[]) {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.authUser) {
      return res.status(401).json({ error: 'Authentication required.' });
    }
    if (!allowedRoles.includes(req.authUser.role)) {
      return res.status(403).json({ error: 'Permission denied for your current role.' });
    }
    next();
  };
}

export function createSessionForUser(user: UserRow, deviceId: string, deviceName: string) {
  const sessionId = `ses_${crypto.randomBytes(12).toString('hex')}`;
  const now = new Date().toISOString();
  const expiresAt = new Date(Date.now() + 1000 * 60 * 60 * 24 * 7).toISOString();
  db.prepare(`
    INSERT INTO sessions (id, user_id, device_id, device_name, expires_at, created_at, last_seen_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `).run(sessionId, user.id, deviceId, deviceName, expiresAt, now, now);

  const token = signSessionToken(sessionId, user.id);
  writeAuditLog(user.id, user.full_name, 'AUTH_LOGIN', 'session', sessionId, `Signed in on ${deviceName}`);
  return {
    token,
    user: sanitizeUser(user),
    deviceId,
    deviceName,
    expiresAt,
  };
}
