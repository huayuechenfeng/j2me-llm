'use strict';

const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');

const MAX_BODY_BYTES = 12 * 1024 * 1024;
const MAX_SEARCH_RESPONSE_BYTES = 512 * 1024;
const SEARCH_PROVIDERS = new Set(['free', 'searxng', 'brave', 'tavily', 'exa', 'custom']);

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
        reject(Object.assign(new Error('Request body exceeds 12 MB'), { status: 413 }));
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

function searchDefaultUrl(provider) {
  if (provider === 'free') {
    return 'https://api.duckduckgo.com/?q={query}&format=json&no_html=1&skip_disambig=1';
  }
  if (provider === 'searxng') {
    return 'https://search.inetol.net/search?q={query}&format=json&categories=general';
  }
  if (provider === 'brave') {
    return 'https://api.search.brave.com/res/v1/web/search?q={query}&count={count}';
  }
  if (provider === 'tavily') return 'https://api.tavily.com/search';
  if (provider === 'exa') return 'https://api.exa.ai/search';
  return '';
}

function expandSearchUrl(template, query, count) {
  return template
    .split('{query}').join(encodeURIComponent(query))
    .split('{count}').join(String(count));
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
  config.searchProvider = String(config.searchProvider || 'free').toLowerCase();
  if (config.searchProvider === 'free-composite') config.searchProvider = 'free';
  if (config.searchProvider === 'public-searxng') config.searchProvider = 'searxng';
  if (!SEARCH_PROVIDERS.has(config.searchProvider)) {
    throw new Error('SEARCH_PROVIDER must be free, searxng, brave, tavily, exa, or custom');
  }
  config.upstreamSearchUrl = config.upstreamSearchUrl
    || searchDefaultUrl(config.searchProvider);
  if (!config.upstreamSearchUrl) {
    throw new Error('UPSTREAM_SEARCH_URL is required for the custom search provider');
  }
  const searchUrl = new URL(expandSearchUrl(config.upstreamSearchUrl, 'test', 1));
  if (searchUrl.protocol !== 'https:' && !config.allowInsecureUpstream) {
    throw new Error('UPSTREAM_SEARCH_URL must use HTTPS');
  }
  if (['brave', 'tavily', 'exa'].includes(config.searchProvider)
      && !config.upstreamSearchApiKey) {
    throw new Error('UPSTREAM_SEARCH_API_KEY is required for this search provider');
  }
  return config;
}

async function readJsonResponse(upstream) {
  const declared = Number(upstream.headers.get('content-length') || 0);
  if (declared > MAX_SEARCH_RESPONSE_BYTES) {
    throw Object.assign(new Error('Search response exceeds 512 KB'), { status: 502 });
  }
  const bytes = Buffer.from(await upstream.arrayBuffer());
  if (bytes.length > MAX_SEARCH_RESPONSE_BYTES) {
    throw Object.assign(new Error('Search response exceeds 512 KB'), { status: 502 });
  }
  let value;
  try {
    value = JSON.parse(bytes.toString('utf8'));
  } catch (_) {
    throw Object.assign(new Error('Search upstream returned invalid JSON'), { status: 502 });
  }
  if (!upstream.ok) {
    const detail = value && (value.message || value.detail
      || (value.error && value.error.message));
    throw Object.assign(new Error(detail || 'Search upstream rejected the request'),
      { status: upstream.status });
  }
  return value;
}

function genericResults(payload) {
  if (Array.isArray(payload)) return payload;
  if (payload && Array.isArray(payload.results)) return payload.results;
  if (payload && payload.web && Array.isArray(payload.web.results)) return payload.web.results;
  if (payload && Array.isArray(payload.data)) return payload.data;
  return [];
}

function normalizeGeneric(payload, limit) {
  return genericResults(payload).slice(0, limit).map((item) => ({
    title: String(item.title || item.name || ''),
    url: String(item.url || item.link || item.href || ''),
    snippet: String(item.content || item.snippet || item.description || item.text || ''),
  })).filter((item) => item.url);
}

function duckResults(payload, limit) {
  const results = [];
  if (payload.AbstractURL) {
    results.push({
      title: String(payload.Heading || payload.AbstractURL),
      url: String(payload.AbstractURL),
      snippet: String(payload.AbstractText || ''),
    });
  }
  function addTopics(topics) {
    for (const topic of topics || []) {
      if (results.length >= limit) return;
      if (Array.isArray(topic.Topics)) addTopics(topic.Topics);
      else if (topic.FirstURL) {
        results.push({
          title: String(topic.Text || topic.FirstURL),
          url: String(topic.FirstURL),
          snippet: String(topic.Text || ''),
        });
      }
    }
  }
  addTopics(payload.RelatedTopics);
  return results.slice(0, limit);
}

async function fetchSearch(config, query, count) {
  const provider = config.searchProvider;
  const url = expandSearchUrl(config.upstreamSearchUrl, query, count);
  const headers = {
    accept: 'application/json',
    'user-agent': 'J2ME-LLM-Gateway/0.4.1',
  };
  let method = 'GET';
  let body;
  if (provider === 'brave') {
    headers['x-subscription-token'] = config.upstreamSearchApiKey;
  } else if (provider === 'tavily') {
    method = 'POST';
    headers['content-type'] = 'application/json; charset=utf-8';
    body = JSON.stringify({
      api_key: config.upstreamSearchApiKey,
      query,
      max_results: count,
      include_answer: false,
    });
  } else if (provider === 'exa') {
    method = 'POST';
    headers['content-type'] = 'application/json; charset=utf-8';
    headers['x-api-key'] = config.upstreamSearchApiKey;
    body = JSON.stringify({
      query,
      numResults: count,
      contents: { text: { maxCharacters: 1200 } },
    });
  } else if (provider === 'custom' && config.upstreamSearchApiKey) {
    headers.authorization = 'Bearer ' + config.upstreamSearchApiKey;
  }
  const upstream = await fetch(url, { method, headers, body, redirect: 'error' });
  const payload = await readJsonResponse(upstream);
  let results = provider === 'free'
    ? duckResults(payload, count)
    : normalizeGeneric(payload, count);

  if (provider === 'free' && results.length < count) {
    const wikiHost = /[\u3400-\u9fff]/.test(query) ? 'zh.wikipedia.org' : 'en.wikipedia.org';
    const wikiUrl = 'https://' + wikiHost + '/w/api.php?action=opensearch&search='
      + encodeURIComponent(query) + '&limit=' + count + '&namespace=0&format=json';
    const wiki = await readJsonResponse(await fetch(wikiUrl, {
      headers: { accept: 'application/json', 'user-agent': 'J2ME-LLM-Gateway/0.4.1' },
      redirect: 'error',
    }));
    if (Array.isArray(wiki) && Array.isArray(wiki[1]) && Array.isArray(wiki[3])) {
      for (let index = 0; index < wiki[1].length && results.length < count; index += 1) {
        const item = {
          title: String(wiki[1][index] || ''),
          url: String(wiki[3][index] || ''),
          snippet: String((wiki[2] && wiki[2][index]) || ''),
        };
        if (item.url && !results.some((existing) => existing.url === item.url)) {
          results.push(item);
        }
      }
    }
  }
  return { results: results.slice(0, count) };
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
  const secrets = [config.upstreamApiKey, config.upstreamSearchApiKey, config.deviceToken];
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
        jsonReply(response, 200, {
          ok: true,
          service: 'j2me-llm-gateway',
          searchProvider: config.searchProvider,
        });
        return;
      }

      const isChat = request.method === 'POST' && url.pathname === '/v1/chat/completions';
      const isModels = request.method === 'GET' && url.pathname === '/v1/models';
      const isSearch = request.method === 'GET' && url.pathname === '/v1/search';
      if (!isChat && !isModels && !isSearch) {
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
            'user-agent': 'J2ME-LLM-Gateway/0.4.1',
          },
          redirect: 'error',
        });
        await forwardResponse(upstream, response);
        return;
      }

      if (isSearch) {
        const query = String(url.searchParams.get('q') || '').trim();
        const requestedCount = Number(url.searchParams.get('count') || 5);
        const count = Number.isFinite(requestedCount)
          ? Math.max(1, Math.min(10, Math.floor(requestedCount))) : 5;
        if (!query || query.length > 1024) {
          jsonReply(response, 400, { error: { message: 'q must contain 1 to 1024 characters' } });
          return;
        }
        jsonReply(response, 200, await fetchSearch(config, query, count));
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
          'user-agent': 'J2ME-LLM-Gateway/0.4.1',
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
    searchProvider: environment.SEARCH_PROVIDER,
    upstreamSearchUrl: environment.UPSTREAM_SEARCH_URL,
    upstreamSearchApiKey: environment.UPSTREAM_SEARCH_API_KEY,
    deviceToken: environment.DEVICE_TOKEN,
    logErrors: environment.LOG_ERRORS === '1',
  };
}

function parseConfigText(text) {
  const values = {};
  const lines = String(text || '').replace(/^\uFEFF/, '').split(/\r?\n/);
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const separator = line.indexOf('=');
    if (separator <= 0) continue;
    const key = line.slice(0, separator).trim();
    if (!/^[A-Z][A-Z0-9_]*$/.test(key)) continue;
    values[key] = line.slice(separator + 1).trim();
  }
  return values;
}

function runtimeEnvironment(environment, args) {
  const result = Object.assign({}, environment);
  const configIndex = args.indexOf('--config');
  if (configIndex >= 0) {
    const configPath = args[configIndex + 1];
    if (!configPath) throw new Error('--config requires a gateway.conf path');
    Object.assign(result, parseConfigText(fs.readFileSync(configPath, 'utf8')));
  }
  return result;
}

if (require.main === module) {
  const environment = runtimeEnvironment(process.env, process.argv.slice(2));
  const host = environment.HOST || '127.0.0.1';
  const port = Number(environment.PORT || 8787);
  const config = validateConfig(fromEnvironment(environment));
  const server = createGateway(config);
  server.listen(port, host, () => {
    console.log('J2ME LLM gateway listening on http://' + host + ':' + port);
    console.log('Search provider: ' + config.searchProvider);
  });
}

module.exports = {
  createGateway,
  fromEnvironment,
  parseConfigText,
  runtimeEnvironment,
  validateConfig,
};
