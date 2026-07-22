package com.chihoko.j2mellm.net;

import java.io.IOException;
import java.util.Vector;

/**
 * Incrementally extracts root data[].id strings from an OpenAI-compatible
 * model-list response. It deliberately does not build a JSON object tree.
 */
public final class ModelCatalogParser {
    public static final int DEFAULT_MAX_MODELS = 64;
    public static final int DEFAULT_MAX_BYTES = 131072;

    private static final int TYPE_OBJECT = 1;
    private static final int TYPE_ARRAY = 2;

    private static final int OBJECT_FIRST_KEY = 1;
    private static final int OBJECT_NEXT_KEY = 2;
    private static final int OBJECT_COLON = 3;
    private static final int OBJECT_VALUE = 4;
    private static final int OBJECT_COMMA = 5;
    private static final int ARRAY_FIRST_VALUE = 6;
    private static final int ARRAY_NEXT_VALUE = 7;
    private static final int ARRAY_COMMA = 8;

    private static final int KEY_OTHER = 0;
    private static final int KEY_DATA = 1;
    private static final int KEY_ID = 2;

    private static final int STRING_KEY = 1;
    private static final int STRING_VALUE = 2;
    private static final int MAX_DEPTH = 20;
    private static final int MAX_KEY_CHARS = 32;
    private static final int MAX_ID_CHARS = 256;

    private final int maxModels;
    private final int maxBytes;
    private final Vector modelIds = new Vector();
    private final int[] types = new int[MAX_DEPTH];
    private final int[] states = new int[MAX_DEPTH];
    private final int[] keys = new int[MAX_DEPTH];
    private final boolean[] rootObjects = new boolean[MAX_DEPTH];
    private final boolean[] dataArrays = new boolean[MAX_DEPTH];
    private final boolean[] modelObjects = new boolean[MAX_DEPTH];

    private int depth;
    private int byteCount;
    private boolean rootSeen;
    private boolean rootComplete;
    private boolean sawDataArray;
    private boolean truncated;

    private boolean inString;
    private int stringRole;
    private boolean stringIsModelId;
    private boolean stringEscape;
    private int unicodeDigits;
    private int unicodeValue;
    private StringBuffer stringToken;
    private boolean stringOverflow;

    private boolean inPrimitive;
    private final StringBuffer primitive = new StringBuffer(16);
    private boolean primitiveOverflow;

    private int utf8Remaining;
    private int utf8Value;
    private int utf8Minimum;

    public ModelCatalogParser() {
        this(DEFAULT_MAX_MODELS, DEFAULT_MAX_BYTES);
    }

    public ModelCatalogParser(int modelLimit, int byteLimit) {
        if (modelLimit < 1) throw new IllegalArgumentException("modelLimit");
        if (byteLimit < 1) throw new IllegalArgumentException("byteLimit");
        maxModels = modelLimit;
        maxBytes = byteLimit;
    }

    public void feed(byte[] data, int offset, int length) throws IOException {
        if (data == null) throw new NullPointerException("data");
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException();
        }
        if (byteCount > maxBytes - length) {
            throw new IOException("模型列表响应超过内存安全限制（" + maxBytes + " 字节）");
        }
        byteCount += length;
        int end = offset + length;
        int i;
        for (i = offset; i < end; i++) feedByte(data[i] & 0xff);
    }

    public Vector finish() throws IOException {
        if (utf8Remaining != 0) throw error("UTF-8 字符不完整");
        if (inString) throw error("字符串未结束");
        if (inPrimitive) endPrimitive();
        if (depth != 0) throw error("JSON 容器未结束");
        if (!rootSeen || !rootComplete) throw error("缺少 JSON 根对象");
        if (!sawDataArray) throw error("响应中没有 data 数组");
        Vector copy = new Vector(modelIds.size());
        int i;
        for (i = 0; i < modelIds.size(); i++) copy.addElement(modelIds.elementAt(i));
        return copy;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public int getByteCount() {
        return byteCount;
    }

    private void feedByte(int value) throws IOException {
        if (utf8Remaining > 0) {
            if ((value & 0xc0) != 0x80) throw error("UTF-8 连续字节非法");
            utf8Value = (utf8Value << 6) | (value & 0x3f);
            utf8Remaining--;
            if (utf8Remaining == 0) {
                int codePoint = utf8Value;
                if (codePoint < utf8Minimum || codePoint > 0x10ffff
                        || (codePoint >= 0xd800 && codePoint <= 0xdfff)) {
                    throw error("UTF-8 字符非法");
                }
                if (codePoint < 0x10000) {
                    feedChar((char) codePoint);
                } else {
                    codePoint -= 0x10000;
                    feedChar((char) (0xd800 | (codePoint >> 10)));
                    feedChar((char) (0xdc00 | (codePoint & 0x3ff)));
                }
            }
            return;
        }
        if (value < 0x80) {
            feedChar((char) value);
        } else if ((value & 0xe0) == 0xc0) {
            utf8Remaining = 1;
            utf8Value = value & 0x1f;
            utf8Minimum = 0x80;
        } else if ((value & 0xf0) == 0xe0) {
            utf8Remaining = 2;
            utf8Value = value & 0x0f;
            utf8Minimum = 0x800;
        } else if ((value & 0xf8) == 0xf0) {
            utf8Remaining = 3;
            utf8Value = value & 0x07;
            utf8Minimum = 0x10000;
        } else {
            throw error("UTF-8 起始字节非法");
        }
    }

    private void feedChar(char ch) throws IOException {
        if (inString) {
            feedStringChar(ch);
            return;
        }
        if (inPrimitive) {
            if (isWhitespace(ch) || ch == ',' || ch == ']' || ch == '}') {
                endPrimitive();
                feedChar(ch);
            } else {
                if (primitive.length() < 32) primitive.append(ch);
                else primitiveOverflow = true;
            }
            return;
        }
        if (isWhitespace(ch)) return;
        if (rootComplete) throw error("JSON 根对象后存在多余内容");
        if (ch == '"') {
            startString();
        } else if (ch == '{') {
            startContainer(TYPE_OBJECT);
        } else if (ch == '[') {
            startContainer(TYPE_ARRAY);
        } else if (ch == '}') {
            closeContainer(TYPE_OBJECT);
        } else if (ch == ']') {
            closeContainer(TYPE_ARRAY);
        } else if (ch == ':') {
            readColon();
        } else if (ch == ',') {
            readComma();
        } else if (ch == 't' || ch == 'f' || ch == 'n' || ch == '-'
                || (ch >= '0' && ch <= '9')) {
            startPrimitive(ch);
        } else {
            throw error("无法识别的 JSON 字符");
        }
    }

    private void startString() throws IOException {
        if (depth == 0) throw error("模型列表根值必须是对象");
        int top = depth - 1;
        if (types[top] == TYPE_OBJECT
                && (states[top] == OBJECT_FIRST_KEY || states[top] == OBJECT_NEXT_KEY)) {
            stringRole = STRING_KEY;
            stringIsModelId = false;
        } else {
            stringRole = STRING_VALUE;
            stringIsModelId = modelObjects[top] && keys[top] == KEY_ID;
            markValueStarted();
        }
        inString = true;
        stringEscape = false;
        unicodeDigits = 0;
        unicodeValue = 0;
        stringOverflow = false;
        stringToken = new StringBuffer(stringRole == STRING_KEY ? 12 : 32);
    }

    private void feedStringChar(char ch) throws IOException {
        if (unicodeDigits > 0) {
            int digit = hexDigit(ch);
            if (digit < 0) throw error("Unicode 转义非法");
            unicodeValue = (unicodeValue << 4) | digit;
            unicodeDigits--;
            if (unicodeDigits == 0) {
                appendStringChar((char) unicodeValue);
                stringEscape = false;
            }
            return;
        }
        if (stringEscape) {
            switch (ch) {
                case '"': appendStringChar('"'); break;
                case '\\': appendStringChar('\\'); break;
                case '/': appendStringChar('/'); break;
                case 'b': appendStringChar('\b'); break;
                case 'f': appendStringChar('\f'); break;
                case 'n': appendStringChar('\n'); break;
                case 'r': appendStringChar('\r'); break;
                case 't': appendStringChar('\t'); break;
                case 'u':
                    unicodeDigits = 4;
                    unicodeValue = 0;
                    return;
                default: throw error("字符串转义非法");
            }
            stringEscape = false;
        } else if (ch == '"') {
            finishString();
        } else if (ch == '\\') {
            stringEscape = true;
        } else if (ch < 0x20) {
            throw error("字符串包含控制字符");
        } else {
            appendStringChar(ch);
        }
    }

    private void appendStringChar(char ch) {
        int limit = stringRole == STRING_KEY ? MAX_KEY_CHARS : MAX_ID_CHARS;
        if (stringRole == STRING_KEY || stringIsModelId) {
            if (stringToken.length() < limit) stringToken.append(ch);
            else stringOverflow = true;
        }
    }

    private void finishString() throws IOException {
        inString = false;
        int top = depth - 1;
        if (stringRole == STRING_KEY) {
            if (types[top] != TYPE_OBJECT) throw error("数组中不能出现对象键");
            if (stringOverflow) {
                keys[top] = KEY_OTHER;
            } else if (rootObjects[top] && "data".equals(stringToken.toString())) {
                keys[top] = KEY_DATA;
            } else if (modelObjects[top] && "id".equals(stringToken.toString())) {
                keys[top] = KEY_ID;
            } else {
                keys[top] = KEY_OTHER;
            }
            states[top] = OBJECT_COLON;
        } else if (stringIsModelId) {
            if (stringOverflow) throw error("模型 ID 过长");
            addModelId(stringToken.toString());
        }
        stringToken = null;
    }

    private void startContainer(int type) throws IOException {
        boolean isRoot = depth == 0;
        boolean isData = false;
        boolean isModel = false;
        if (isRoot) {
            if (rootSeen || type != TYPE_OBJECT) throw error("模型列表根值必须是对象");
            rootSeen = true;
        } else {
            int top = depth - 1;
            isData = type == TYPE_ARRAY && rootObjects[top] && keys[top] == KEY_DATA;
            isModel = type == TYPE_OBJECT && dataArrays[top];
            markValueStarted();
        }
        if (depth >= MAX_DEPTH) throw error("JSON 嵌套过深");
        types[depth] = type;
        states[depth] = type == TYPE_OBJECT ? OBJECT_FIRST_KEY : ARRAY_FIRST_VALUE;
        keys[depth] = KEY_OTHER;
        rootObjects[depth] = isRoot;
        dataArrays[depth] = isData;
        modelObjects[depth] = isModel;
        depth++;
        if (isData) sawDataArray = true;
    }

    private void closeContainer(int expectedType) throws IOException {
        if (depth == 0) throw error("JSON 结束符多余");
        int top = depth - 1;
        if (types[top] != expectedType) throw error("JSON 容器结束符不匹配");
        int state = states[top];
        if (expectedType == TYPE_OBJECT) {
            if (state != OBJECT_FIRST_KEY && state != OBJECT_COMMA) {
                throw error("对象值不完整");
            }
        } else if (state != ARRAY_FIRST_VALUE && state != ARRAY_COMMA) {
            throw error("数组值不完整");
        }
        depth--;
        if (depth == 0) rootComplete = true;
    }

    private void readColon() throws IOException {
        if (depth == 0) throw error("冒号位置非法");
        int top = depth - 1;
        if (types[top] != TYPE_OBJECT || states[top] != OBJECT_COLON) {
            throw error("冒号位置非法");
        }
        states[top] = OBJECT_VALUE;
    }

    private void readComma() throws IOException {
        if (depth == 0) throw error("逗号位置非法");
        int top = depth - 1;
        if (types[top] == TYPE_OBJECT && states[top] == OBJECT_COMMA) {
            states[top] = OBJECT_NEXT_KEY;
            keys[top] = KEY_OTHER;
        } else if (types[top] == TYPE_ARRAY && states[top] == ARRAY_COMMA) {
            states[top] = ARRAY_NEXT_VALUE;
        } else {
            throw error("逗号位置非法");
        }
    }

    private void startPrimitive(char first) throws IOException {
        if (depth == 0) throw error("模型列表根值必须是对象");
        markValueStarted();
        primitive.setLength(0);
        primitive.append(first);
        primitiveOverflow = false;
        inPrimitive = true;
    }

    private void endPrimitive() throws IOException {
        inPrimitive = false;
        if (primitiveOverflow || !validPrimitive(primitive.toString())) {
            throw error("JSON 常量或数字非法");
        }
        primitive.setLength(0);
    }

    private void markValueStarted() throws IOException {
        if (depth == 0) throw error("缺少 JSON 容器");
        int top = depth - 1;
        if (types[top] == TYPE_OBJECT && states[top] == OBJECT_VALUE) {
            states[top] = OBJECT_COMMA;
        } else if (types[top] == TYPE_ARRAY
                && (states[top] == ARRAY_FIRST_VALUE || states[top] == ARRAY_NEXT_VALUE)) {
            states[top] = ARRAY_COMMA;
        } else {
            throw error("JSON 值位置非法");
        }
    }

    private void addModelId(String id) {
        if (id == null || id.length() == 0 || containsModel(id)) return;
        if (modelIds.size() >= maxModels) {
            truncated = true;
            return;
        }
        modelIds.addElement(id);
    }

    private boolean containsModel(String id) {
        int i;
        for (i = 0; i < modelIds.size(); i++) {
            if (id.equals(modelIds.elementAt(i))) return true;
        }
        return false;
    }

    private boolean validPrimitive(String value) {
        if ("true".equals(value) || "false".equals(value) || "null".equals(value)) return true;
        int length = value.length();
        int position = 0;
        if (position < length && value.charAt(position) == '-') position++;
        if (position >= length) return false;
        if (value.charAt(position) == '0') {
            position++;
        } else {
            if (!isDigitOneToNine(value.charAt(position))) return false;
            while (++position < length && isDigit(value.charAt(position))) { }
        }
        if (position < length && value.charAt(position) == '.') {
            position++;
            int start = position;
            while (position < length && isDigit(value.charAt(position))) position++;
            if (position == start) return false;
        }
        if (position < length && (value.charAt(position) == 'e' || value.charAt(position) == 'E')) {
            position++;
            if (position < length && (value.charAt(position) == '+' || value.charAt(position) == '-')) {
                position++;
            }
            int start = position;
            while (position < length && isDigit(value.charAt(position))) position++;
            if (position == start) return false;
        }
        return position == length;
    }

    private boolean isWhitespace(char ch) {
        return ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t';
    }

    private boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private boolean isDigitOneToNine(char ch) {
        return ch >= '1' && ch <= '9';
    }

    private int hexDigit(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'a' && ch <= 'f') return ch - 'a' + 10;
        if (ch >= 'A' && ch <= 'F') return ch - 'A' + 10;
        return -1;
    }

    private IOException error(String message) {
        return new IOException(message + "（字节 " + byteCount + "）");
    }
}
