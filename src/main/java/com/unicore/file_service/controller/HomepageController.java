package com.unicore.file_service.controller;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.unicore.file_service.dto.FileItemDTO;
import com.unicore.file_service.dto.MessageDTO;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;




@Controller
public class HomepageController {
    private static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String USER_IDENTIFIER_KEY = "MY_DUMMY_USER";

    @Value("${google.oauth.callback.uri}")
    private  String CALLBACK_URI;

    @Value("${google.secret.key.path}")
    private Resource gdSecretKeys;

    @Value("${google.credentials.folder.path}")
    private Resource credentialsFolder;

    private GoogleAuthorizationCodeFlow flow;

    @PostConstruct
    public void init() throws IOException {
        GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(gdSecretKeys.getInputStream()));
        flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(credentialsFolder.getFile())).build();
    }

    @GetMapping
    public String homepage() throws IOException {
        boolean isUserAuthenticated = false;

        Credential credential = flow.loadCredential(USER_IDENTIFIER_KEY);
        if (credential != null) {
            boolean tokenValid = credential.refreshToken();
            if (tokenValid) {
                isUserAuthenticated = true;
            }
        }

        return isUserAuthenticated ? "dashboard.html" : "index.html";
    }

    @GetMapping("/googlesignin")
    public void googleSignin(HttpServletResponse response) throws IOException {
        GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();
        String redirectUrl = url.setRedirectUri(CALLBACK_URI)
            .setAccessType("offline")
            .build();

        response.sendRedirect(redirectUrl);
    }
    
    @GetMapping("/auth")
    public String saveAuthorizationCode(HttpServletRequest request) throws IOException {
        String code = request.getParameter("code");
        if (code != null) {
            saveToken(code);
            return "dashboard.html";
        }
        return "index.html";
    }

    private void saveToken(String code) throws IOException {
        GoogleTokenResponse response = flow.newTokenRequest(code).setRedirectUri(CALLBACK_URI).execute();
        flow.createAndStoreCredential(response, USER_IDENTIFIER_KEY);
    }
    
    @GetMapping("/create")
    public void createFile(HttpServletResponse response) throws IOException {
        Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);

        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
            .setApplicationName("Unicore")
            .build();

        File file = new File();
        file.setName("site-logo.png");

        // add file path
        FileContent content = new FileContent("image/png", new java.io.File("C:/Users/ADMIN/Downloads/site-logo.png"));
        File uploadedFile = drive.files().create(file, content).setFields("id").execute();

        String fileRef = String.format("{file_id: '%s'}", uploadedFile.getId());

        response.getWriter().write(fileRef);
    }
    
    @GetMapping(value={"/listfiles"}, produces={"application/json"})
    public @ResponseBody List<FileItemDTO> listFiles() throws IOException {
        Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);

        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
            .setApplicationName("Unicore")
            .build();

        List<FileItemDTO> responseList = new ArrayList<>();
        FileList fileList = drive.files().list().setFields("files(id,name,thumbnailLink)").execute();

        for (File file : fileList.getFiles()) {
            FileItemDTO item = FileItemDTO.builder()
                .id(file.getId())
                .name(file.getName())
                .thumbnailLink(file.getThumbnailLink())
                .build();
            
            responseList.add(item);
        }

        return responseList;
    }
    
    @DeleteMapping("/deletefile/{fileId}")
    public @ResponseBody MessageDTO deleteFile(@PathVariable String fileId) throws IOException {
        Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);

        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
            .setApplicationName("Unicore")
            .build();
        
        drive.files().delete(fileId).execute();

        return new MessageDTO("File " + fileId + " has been deleted.");
    }

    @GetMapping("/createfolder/{folderName}")
    public @ResponseBody MessageDTO createFolder(@PathVariable String folderName) throws IOException {
        Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);

        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
            .setApplicationName("Unicore")
            .build();

        File file = new File();
        file.setName(folderName);
        file.setMimeType("application/vnd.google-apps.folder");

        drive.files().create(file).execute();

        return new MessageDTO("Folder has been created successfully.");
    }
    
}
