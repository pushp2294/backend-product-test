// A minimal SMTP server for tests, built on Node's `net` module (no extra dependency).
// It speaks just enough SMTP for nodemailer to deliver one message, and records the
// envelope recipients (RCPT TO) and the raw message. That lets tests drive the REAL
// SmtpEmailSender.fromConfig over TCP, as it would talk to Mailpit.
import { type AddressInfo, createServer, type Server } from 'node:net';

export interface ReceivedMail {
  /** Envelope recipients: who the server was actually asked to deliver to. */
  recipients: string[];
  /** The raw message: headers, blank line, body. */
  raw: string;
}

export interface FakeSmtpServer {
  port: number;
  received: ReceivedMail[];
  close(): Promise<void>;
}

export async function startFakeSmtpServer(): Promise<FakeSmtpServer> {
  const received: ReceivedMail[] = [];

  const server: Server = createServer((socket) => {
    let buffer = '';
    let inData = false;
    let recipients: string[] = [];

    socket.write('220 fake-smtp ready\r\n');

    socket.on('data', (chunk) => {
      buffer += chunk.toString('utf8');

      // Process as many complete commands (or a complete DATA block) as the buffer holds.
      for (;;) {
        if (inData) {
          // The message ends with a line containing a single "." (CRLF.CRLF).
          const end = buffer.indexOf('\r\n.\r\n');
          if (end === -1) return;
          received.push({ recipients, raw: buffer.slice(0, end) });
          buffer = buffer.slice(end + 5);
          inData = false;
          recipients = [];
          socket.write('250 OK queued\r\n');
          continue;
        }

        const lineEnd = buffer.indexOf('\r\n');
        if (lineEnd === -1) return;
        const line = buffer.slice(0, lineEnd);
        buffer = buffer.slice(lineEnd + 2);
        const command = line.slice(0, 4).toUpperCase();

        if (command === 'EHLO' || command === 'HELO') socket.write('250 fake-smtp\r\n');
        else if (command === 'RCPT') {
          recipients.push(line.replace(/^RCPT TO:\s*<?([^>]*)>?.*$/i, '$1'));
          socket.write('250 OK\r\n');
        } else if (command === 'DATA') {
          inData = true;
          socket.write('354 End data with <CR><LF>.<CR><LF>\r\n');
        } else if (command === 'QUIT') {
          socket.end('221 Bye\r\n');
          return;
        } else socket.write('250 OK\r\n'); // MAIL FROM, RSET, NOOP
      }
    });
  });

  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;

  return {
    port,
    received,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

/** Header lines of a raw message (everything before the first blank line), unfolded. */
export function headerLines(raw: string): string[] {
  const head = raw.split(/\r?\n\r?\n/)[0] ?? '';
  // Long headers may be "folded" onto continuation lines starting with whitespace; join them back.
  return head.replace(/\r?\n[ \t]+/g, ' ').split(/\r?\n/);
}
