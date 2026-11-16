import type { Logger } from '../../src/ports/Logger';

export interface LogEntry {
  level: 'info' | 'warn' | 'error';
  message: string;
  context?: Record<string, unknown>;
}

/** Logger that keeps every entry in memory so tests can assert on what was logged. */
export class RecordingLogger implements Logger {
  readonly entries: LogEntry[] = [];

  info(message: string, context?: Record<string, unknown>): void {
    this.entries.push({ level: 'info', message, context });
  }

  warn(message: string, context?: Record<string, unknown>): void {
    this.entries.push({ level: 'warn', message, context });
  }

  error(message: string, context?: Record<string, unknown>): void {
    this.entries.push({ level: 'error', message, context });
  }

  get infos(): LogEntry[] {
    return this.entries.filter((e) => e.level === 'info');
  }

  get errors(): LogEntry[] {
    return this.entries.filter((e) => e.level === 'error');
  }

  get warnings(): LogEntry[] {
    return this.entries.filter((e) => e.level === 'warn');
  }
}
