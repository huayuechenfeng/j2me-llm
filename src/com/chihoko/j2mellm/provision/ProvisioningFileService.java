package com.chihoko.j2mellm.provision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

/** JSR-75 file access kept outside the normal startup path. */
public final class ProvisioningFileService implements ProvisioningFileController {
    public ProvisioningPackage importFile(String fileUrl) throws IOException {
        FileConnection file = null;
        InputStream input = null;
        try {
            file = open(fileUrl, Connector.READ);
            if (!file.exists() || file.isDirectory()) throw new IOException("配置文件不存在");
            long size = file.fileSize();
            if (size > ProvisioningCodec.MAX_FILE_BYTES) throw new IOException("配置包超过 32 KB");
            input = file.openInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    size > 0 && size <= ProvisioningCodec.MAX_FILE_BYTES ? (int) size : 1024);
            byte[] buffer = new byte[512];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (output.size() + count > ProvisioningCodec.MAX_FILE_BYTES) {
                    throw new IOException("配置包超过 32 KB");
                }
                output.write(buffer, 0, count);
            }
            return ProvisioningCodec.decode(output.toByteArray());
        } finally {
            close(input);
            close(file);
        }
    }

    public void exportFile(String fileUrl, ProvisioningPackage config) throws IOException {
        validateConfigUrl(fileUrl);
        byte[] bytes = ProvisioningCodec.encode(config);
        String temporaryUrl = fileUrl + ".tmp-" + System.currentTimeMillis();
        String finalName = fileName(fileUrl);
        FileConnection target = null;
        FileConnection temporary = null;
        FileConnection verified = null;
        OutputStream output = null;
        boolean temporaryCreated = false;
        boolean renamed = false;
        try {
            target = open(fileUrl, Connector.READ_WRITE);
            if (target.exists()) {
                if (target.isDirectory()) throw new IOException("导出位置是目录");
                throw new IOException("同名备份已存在；为保护旧备份，本次导出已取消");
            }
            close(target);
            target = null;

            temporary = (FileConnection) Connector.open(temporaryUrl, Connector.READ_WRITE);
            if (temporary.exists()) throw new IOException("临时导出文件已存在，请重试");
            temporary.create();
            temporaryCreated = true;
            output = temporary.openOutputStream();
            output.write(bytes);
            output.flush();
            close(output);
            output = null;

            long writtenSize = temporary.fileSize();
            if ((writtenSize >= 0 && writtenSize != bytes.length)
                    || !sameContents(temporary, bytes)) {
                throw new IOException("临时备份回读校验失败");
            }

            /*
             * Recheck immediately before the same-directory rename. JSR-75 must
             * fail rename when the destination exists; no code path truncates or
             * deletes the destination.
             */
            target = open(fileUrl, Connector.READ_WRITE);
            if (target.exists()) {
                throw new IOException("导出目标刚刚被占用；旧文件保持不变");
            }
            close(target);
            target = null;
            temporary.rename(finalName);
            renamed = true;
            close(temporary);
            temporary = null;

            verified = open(fileUrl, Connector.READ);
            if (!verified.exists() || verified.isDirectory()
                    || !sameContents(verified, bytes)) {
                throw new IOException("备份改名后的回读校验失败");
            }
        } finally {
            close(output);
            close(verified);
            close(target);
            close(temporary);
            if (temporaryCreated && !renamed) deleteTemporary(temporaryUrl);
        }
    }

    public void deleteFile(String fileUrl) throws IOException {
        FileConnection file = null;
        try {
            file = open(fileUrl, Connector.READ_WRITE);
            if (file.exists() && !file.isDirectory()) file.delete();
        } finally {
            close(file);
        }
    }

    public String defaultExportUrl() throws IOException {
        Enumeration roots = FileSystemRegistry.listRoots();
        String fallback = null;
        String fileName = "J2ME-LLM-backup-" + System.currentTimeMillis() + ".j2cfg";
        while (roots.hasMoreElements()) {
            String root = "file:///" + (String) roots.nextElement();
            if (fallback == null) fallback = root + fileName;
            FileConnection directory = null;
            try {
                directory = (FileConnection) Connector.open(root, Connector.READ_WRITE);
                if (directory.exists() && directory.isDirectory() && directory.canWrite()) {
                    return root + fileName;
                }
            } catch (Throwable ignored) {
            } finally {
                close(directory);
            }
        }
        if (fallback != null) return fallback;
        throw new IOException("手机没有可写文件系统");
    }

    private FileConnection open(String fileUrl, int mode) throws IOException {
        validateConfigUrl(fileUrl);
        return (FileConnection) Connector.open(fileUrl, mode);
    }

    private void validateConfigUrl(String fileUrl) throws IOException {
        if (fileUrl == null || !fileUrl.startsWith("file:///")
                || !fileUrl.toLowerCase().endsWith(".j2cfg")) {
            throw new IOException("请选择 .j2cfg 文件");
        }
    }

    private String fileName(String fileUrl) throws IOException {
        int slash = fileUrl.lastIndexOf('/');
        if (slash < 0 || slash == fileUrl.length() - 1) {
            throw new IOException("导出文件名无效");
        }
        return fileUrl.substring(slash + 1);
    }

    private boolean sameContents(FileConnection file, byte[] expected) throws IOException {
        InputStream input = null;
        try {
            input = file.openInputStream();
            byte[] buffer = new byte[512];
            int offset = 0;
            while (offset < expected.length) {
                int wanted = expected.length - offset;
                if (wanted > buffer.length) wanted = buffer.length;
                int count = input.read(buffer, 0, wanted);
                if (count < 0) return false;
                if (count == 0) continue;
                int i;
                for (i = 0; i < count; i++) {
                    if (buffer[i] != expected[offset + i]) return false;
                }
                offset += count;
            }
            return input.read() < 0;
        } finally {
            close(input);
        }
    }

    private void deleteTemporary(String temporaryUrl) {
        FileConnection temporary = null;
        try {
            temporary = (FileConnection) Connector.open(temporaryUrl, Connector.READ_WRITE);
            if (temporary.exists() && !temporary.isDirectory()) temporary.delete();
        } catch (Throwable ignored) {
        } finally {
            close(temporary);
        }
    }

    private void close(InputStream input) {
        if (input != null) try { input.close(); } catch (IOException ignored) { }
    }

    private void close(OutputStream output) {
        if (output != null) try { output.close(); } catch (IOException ignored) { }
    }

    private void close(FileConnection file) {
        if (file != null) try { file.close(); } catch (IOException ignored) { }
    }
}





