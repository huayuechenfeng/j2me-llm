'use strict';

const http = require('node:http');
const crypto = require('node:crypto');

const MAX_BODY_BYTES = 1024 * 1024;

function equalToken(actual, expected) {
  const a = Buffer.from(actual || '', 'utf8');
  const b = Buffer.from(expected || '', 'utf8');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function bearerToken(request) {
  const value = request.headers.authorization || '';
  return value.startsWith('Bearer ') ? value.slice(7) : '';
}

function readBody(request, limit) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    request.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(Object.assign(new Error('Request body exceeds 1 MB'), { status: 413 }));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on('end', () => resolve(Buffer.concat(chunks)));
    request.on('error', reject);
  });
}

function jsonReply(response, status, value) {
  const body = Buffer.from(JSON.stringify(value), 'utf8');
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': String(body.length),
    'cache-control': 'no-store',
  });
  response.end(body);
}

function deriveModelsUrl(chatUrl) {
  const url = new URL(chatUrl.toString());
  if (!/\/chat\/completions\/?$/.test(url.pathname)) {
    throw new Error('UPSTREAM_MODELS_URL is required when UPSTREAM_URL does not end in /chat/completions');
  }
  url.pathname = url.pathname.replace(/\/chat\/completions\/?$/, '/models');
  return url;
}

function validateConfig(input) {
  const config = Object.assign({}, input);
  if (!config.upstreamUrl) config.upstreamUrl = 'https://api.openai.com/v1/chat/completions';
  if (!config.upstreamApiKey) throw new Error('UPSTREAM_API_KEY is required');
  if (!config.deviceToken || config.deviceToken.length < 12) {
    throw new Error('DEVICE_TOKEN is required and must contain at least 12 characters');
  }

  const chatUrl = new URL(config.upstreamUrl);
  if (chatUrl.protocol !== 'https:' && !config.allowInsecureUpstream) {
    throw new Error('UPSTREAM_URL must use HTTPS');
  }
  const modelsUrl = config.upstreamModelsUrl
    ? new URL(config.upstreamModelsUrl)
    : deriveModelsUrl(chatUrl);
  if (modelsUrl.protocol !== 'https:' && !config.allowInsecureUpstream) {
    throw new Error('UPSTREAM_MODELS_URL must use HTTPS');
  }

  config.upstreamUrl = chatUrl.toString();
  config.upstreamModelsUrl = modelsUrl.toString();
  return config;
}

async function forwardResponse(upstream, response) {
  const headers = {
    'content-type': upstream.headers.get('content-type') || 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
  };
  const requestId = upstream.headers.get('x-request-id');
  if (requestId) headers['x-request-id'] = requestId;
  response.writeHead(upstream.status, headers);

  if (!upstream.body) {
    response.end();
    return;
  }
  const reader = upstream.body.getReader();
  while (true) {
    const part = await reader.read();
    if (part.done) break;
    if (!response.write(Buffer.from(part.value))) {
      await new Promise((resolve) => response.once('drain', resolve));
    }
  }
  response.end();
}

function safeErrorMessage(error, config) {
  let message = error && error.message ? String(error.message) : 'Unknown error';
  const secrets = [config.upstreamApiKey, config.deviceToken];
  for (const secret of secrets) {
    if (secret) message = message.split(secret).join('[redacted]');
  }
  return message;
}

function createGateway(input) {
  const config = validateConfig(input);
  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url, 'http://gateway.local');
      if (request.method === 'GET' && url.pathname === '/health') {
        jsonReply(response, 200, { ok: true, service: 'j2me-llm-gateway' });
        return;
      }

      const isChat = request.method === 'POST' && url.pathname === '/v1/chat/completions';
      const isModels = request.method === 'GET' && url.pathname === '/v1/models';
      if (!isChat && !isModels) {
        jsonReply(response, 404, { error: { message: 'Not found' } });
        return;
      }
      if (!equalToken(bearerToken(request), config.deviceToken)) {
        jsonReply(response, 401, { error: { message: 'Invalid device token' } });
        return;
      }

      if (isModels) {
        const upstream = await fetch(config.upstreamModelsUrl, {
          method: 'GET',
          headers: {
            authorization: 'Bearer ' + config.upstreamApiKey,
            accept: 'application/json',
            'user-agent': 'J2ME-LLM-Gateway/0.2.0',
          },
          redirect: 'error',
        });
        await forwardResponse(upstream, response);
        return;
      }

      const rawBody = await readBody(request, MAX_BODY_BYTES);
      let body;
      try {
        body = JSON.parse(rawBody.toString('utf8'));
      } catch (_) {
        jsonReply(response, 400, { error: { message: 'Request body must be valid JSON' } });
        return;
      }
      if (!Array.isArray(body.messages)) {
        jsonReply(response, 400, { error: { message: 'messages must be an array' } });
        return;
      }
      if (config.upstreamModel) body.model = config.upstreamModel;

      const upstream = await fetch(config.upstreamUrl, {
        method: 'POST',
        headers: {
          authorization: 'Bearer ' + config.upstreamApiKey,
          'content-type': 'application/json; charset=utf-8',
          accept: request.headers.accept || 'text/event-stream, application/json',
          'user-agent': 'J2ME-LLM-Gateway/0.2.0',
        },
        body: JSON.stringify(body),
        redirect: 'error',
      });
      await forwardResponse(upstream, response);
    } catch (error) {
      if (response.headersSent) {
        response.destroy(error);
        return;
      }
      const status = error.status || 502;
      jsonReply(response, status, {
        error: { message: status === 502 ? 'Upstream request failed' : error.message },
      });
      if (config.logErrors) {
        console.error('Gateway error: ' + safeErrorMessage(error, config));
      }
    }
  });
}

function fromEnvironment(environment) {
  return {
    upstreamUrl: environment.UPSTREAM_URL,
    upstreamModelsUrl: environment.UPSTREAM_MODELS_URL,
    upstreamApiKey: environment.UPSTREAM_API_KEY,
    upstreamModel: environment.UPSTREAM_MODEL,
    deviceToken: environment.DEVICE_TOKEN,
    logErrors: environment.LOG_ERRORS === '1',
  };
}

if (require.main === module) {
  const host = process.env.HOST || '127.0.0.1';
  const port = Number(process.env.PORT || 8787);
  const server = createGateway(fromEnvironment(process.env));
  server.listen(port, host, () => {
    console.log('J2ME LLM gateway listening on http://' + host + ':' + port);
  });
}

module.exports = { createGateway, fromEnvironment };

