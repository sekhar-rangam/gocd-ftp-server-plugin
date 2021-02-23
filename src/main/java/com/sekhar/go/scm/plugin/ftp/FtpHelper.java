package com.sekhar.go.scm.plugin.ftp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;

import com.sekhar.go.scm.plugin.model.FTPConfig;

public class FtpHelper {
	FTPConfig gitConfig=null; 
	public FtpHelper(FTPConfig gitConfig) {
        this.gitConfig=gitConfig;
    }
	
	public boolean checkConnection() {
		boolean isConnectionEstablished=true;
		FTPClient ftpClient = null;
        try {
        	ftpClient = new FTPClient();
        	ftpClient.connect(gitConfig.getUrl(), 21);
			int replyCode = ftpClient.getReplyCode();
			if (!FTPReply.isPositiveCompletion(replyCode)) {
				isConnectionEstablished = false;
			}
        } catch (Exception e) {
        	isConnectionEstablished=false;
        }finally {
        	try {
				ftpClient.disconnect();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
        return isConnectionEstablished;
    }
	
	public FTPClient getFTPClient() {
		FTPClient ftpClient = null;
        try {
        	ftpClient = new FTPClient();
        	ftpClient.connect(gitConfig.getUrl(), 21);
			int replyCode = ftpClient.getReplyCode();
			if (!FTPReply.isPositiveCompletion(replyCode)) {
				return null;
			}
			boolean success = ftpClient.login(gitConfig.getUsername(), gitConfig.getPassword());
			if(!success) {
				return null;
			}
        } catch (Exception e) {
            throw new RuntimeException("check connection (ls-remote) failed", e);
        }
        return ftpClient;
    }
	
	public boolean authenticate() {
		boolean success=false;
        try {
        	FTPClient ftpClient = new FTPClient();
        	success = ftpClient.login(gitConfig.getUsername(), gitConfig.getPassword());
        } catch (Exception e) {
            throw new RuntimeException("check connection (ls-remote) failed", e);
        }
        return success;
    }
	
	public void fetchSourceCode(Map<String, Object> response,ArrayList<String> messages,String workingDir) {
        try {
        	FTPClient ftpClient =getFTPClient();
        	downloadRootDirectory(ftpClient, "", "", workingDir);
        } catch (Exception e) {
        	response.put("status", "failure");
            messages.add(e.getMessage());
        }
    }
	
	private void downloadRootDirectory(FTPClient ftpClient, String parentDir, String currentDir, String saveDir)
			throws IOException {
		
		String dirToList = parentDir;
		if (!currentDir.equals("")) {
			dirToList += "/" + currentDir;
		}

		FTPFile[] subFiles = ftpClient.listFiles("/home/ftpuser/forecasting" + dirToList);
		if (subFiles != null && subFiles.length > 0) {
			for (FTPFile aFile : subFiles) {
				String currentFileName = aFile.getName();
				if (currentFileName.equals(".") || currentFileName.equals("..")) {
					// skip parent directory and the directory itself
					continue;
				}
				String filePath = parentDir + "/" + currentDir + "/" + currentFileName;
				if (currentDir.equals("")) {
					filePath = parentDir + "/" + currentFileName;
				}

				String newDirPath = saveDir + parentDir + File.separator + currentDir + File.separator
						+ currentFileName;
				if (currentDir.equals("")) {
					newDirPath = saveDir + parentDir + File.separator + currentFileName;
				}

				if (aFile.isDirectory()) {
					// create the directory in saveDir
					File newDir = new File(newDirPath);
					boolean created = newDir.mkdirs();
					if (created) {
						System.out.println("CREATED the directory: " + newDirPath);
					} else {
						System.out.println("COULD NOT create the directory: " + newDirPath);
					}
					// download the sub directory
					downloadRootDirectory(ftpClient, dirToList, currentFileName, saveDir);
				} else {
					// download the file
					boolean success = downloadSingleFile(ftpClient, filePath, newDirPath);
					if (success) {
						System.out.println("DOWNLOADED the file: " + filePath);
					} else {
						System.out.println("COULD NOT download the file: " + filePath);
					}
				}
			}
		}
	}

	private boolean downloadSingleFile(FTPClient ftpClient, String remoteFilePath, String savePath)
			throws IOException {
		File downloadFile = new File(savePath);

		File parentDir = downloadFile.getParentFile();
		if (!parentDir.exists()) {
			parentDir.mkdir();
		}

		OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(downloadFile));
		try {
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			return ftpClient.retrieveFile(remoteFilePath, outputStream);
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (outputStream != null) {
				outputStream.close();
			}
		}
	}
}
