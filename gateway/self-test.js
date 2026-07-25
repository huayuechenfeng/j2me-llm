'use strict';

const assert = require('node:assert/strict');
const http = require('node:http');
const { createGateway, fromEnvironment } = require('./server');

async function listen(server) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  return server.address().port;
}

async function close(server) {
  await new Promise((resolve) => server.close(resolve));
}

async function main() {
  const upstreamCalls = [];
  const upstream = http.createServer((request, response) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => {
      upstreamCalls.push({
        method: request.method,
        path: request.url,
        authorization: request.headers.authorization,
        userAgent: request.headers['user-agent'],
      });

      if (request.method === 'GET' && request.url === '/v1/models') {
        response.writeHead(206, {
          'content-type': 'application/json; charset=utf-8',
          'x-request-id': 'models-request-id',
        });
        response.write('{"object":"list","data":[');
        response.end('{"id":"model-a"},{"id":"model-b"}]}');
        return;
      }

      if (request.method === 'POST' && request.url === '/v1/chat/completions') {
        const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        assert.equal(body.model, 'gateway-model');
        assert.deepEqual(body.messages, [{ role: 'user', content: 'hi' }]);
        response.writeHead(200, { 'content-type': 'text/event-stream' });
        response.end('data: {"choices":[{"delta":{"content":"ok"}}]}\n\ndata: [DONE]\n\n');
        return;
      }

      response.writeHead(404, { 'content-type': 'application/json' });
      response.end('{"error":"unexpected test route"}');
    });
  });

  const upstreamPort = await listen(upstream);
  const gateway = createGateway({
    upstreamUrl: 'http://127.0.0.1:' + upstreamPort + '/v1/chat/completions',
    upstreamApiKey: 'upstream-secret',
    upstreamModel: 'gateway-model',
    deviceToken: 'device-token-1234',
    allowInsecureUpstream: true,
  });
  const gatewayPort = await listen(gateway);

  try {
    const health = await fetch('http://127.0.0.1:' + gatewayPort + '/health');
    assert.equal(health.status, 200);

    const deniedChat = await fetch(
      'http://127.0.0.1:' + gatewayPort + '/v1/chat/completions',
      { method: 'POST', body: '{}', headers: { 'content-type': 'application/json' } },
    );
    assert.equal(deniedChat.status, 401);

    const deniedModels = await fetch(
      'http://127.0.0.1:' + gatewayPort + '/v1/models',
    );
    assert.equal(deniedModels.status, 401);
    assert.equal(upstreamCalls.length, 0);

    const models = await fetch(
      'http://127.0.0.1:' + gatewayPort + '/v1/models',
      { headers: { authorization: 'Bearer device-token-1234' } },
    );
    assert.equal(models.status, 206);
    assert.equal(models.headers.get('content-type'), 'application/json; charset=utf-8');
    assert.equal(models.headers.get('x-request-id'), 'models-request-id');
    assert.deepEqual(await models.json(), {
      object: 'list',
      data: [{ id: 'model-a' }, { id: 'model-b' }],
    });

    const accepted = await fetch(
      'http://127.0.0.1:' + gatewayPort + '/v1/chat/completions',
      {
        method: 'POST',
        headers: {
          authorization: 'Bearer device-token-1234',
          'content-type': 'application/json',
          accept: 'text/event-stream',
        },
        body: JSON.stringify({
          model: 'phone-model',
          stream: true,
          messages: [{ role: 'user', content: 'hi' }],
        }),
      },
    );
    assert.equal(accepted.status, 200);
    assert.equal(accepted.headers.get('content-type'), 'text/event-stream');
    assert.match(await accepted.text(), /data: \[DONE\]/);

    assert.deepEqual(
      upstreamCalls.map((call) => [call.method, call.path]),
      [['GET', '/v1/models'], ['POST', '/v1/chat/completions']],
    );
    for (const call of upstreamCalls) {
      assert.equal(call.authorization, 'Bearer upstream-secret');
      assert.equal(call.userAgent, 'J2ME-LLM-Gateway/0.3.0');
    }

    assert.throws(
      () => createGateway({
        upstreamUrl: 'http://provider.example/v1/chat/completions',
        upstreamApiKey: 'upstream-secret',
        deviceToken: 'device-token-1234',
      }),
      /UPSTREAM_URL must use HTTPS/,
    );
    assert.throws(
      () => createGateway({
        upstreamUrl: 'https://provider.example/v1/responses',
        upstreamApiKey: 'upstream-secret',
        deviceToken: 'device-token-1234',
      }),
      /UPSTREAM_MODELS_URL is required/,
    );
    assert.throws(
      () => createGateway({
        upstreamUrl: 'https://provider.example/v1/chat/completions',
        upstreamModelsUrl: 'http://provider.example/v1/models',
        upstreamApiKey: 'upstream-secret',
        deviceToken: 'device-token-1234',
      }),
      /UPSTREAM_MODELS_URL must use HTTPS/,
    );

    const envConfig = fromEnvironment({
      UPSTREAM_URL: 'https://provider.example/v1/chat/completions',
      UPSTREAM_MODELS_URL: 'https://catalog.example/v1/models',
      UPSTREAM_API_KEY: 'key',
      UPSTREAM_MODEL: 'model',
      DEVICE_TOKEN: 'device-token-1234',
      ALLOW_INSECURE_UPSTREAM: '1',
      LOG_ERRORS: '1',
    });
    assert.equal(envConfig.upstreamModelsUrl, 'https://catalog.example/v1/models');
    assert.equal(envConfig.allowInsecureUpstream, undefined);
    assert.equal(envConfig.logErrors, true);

    console.log('Gateway self-test passed');
  } finally {
    await close(gateway);
    await close(upstream);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
