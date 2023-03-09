package pro.sawit;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.itextpdf.awt.AsianFontMapper;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import net.sourceforge.tess4j.Tesseract;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Hello world!
 *
 */
public class Main 
{
    private static final String APPLICATION_NAME = "SawitPro OCR";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES =
            Collections.singletonList(DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    private static final String FOLDER_NAME = "SawitPro";

    public static void main( String[] args ) throws Exception {
//        uploadToGoogleDrive();
        getTextFromImage();
    }

    private static void getTextFromImage() throws Exception {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");
        tesseract.setPageSegMode(1);
        tesseract.setOcrEngineMode(1);

        StringBuilder result = new StringBuilder();
        StringBuilder engResult = new StringBuilder();
        StringBuilder chiResult = new StringBuilder();
        String imageFormat = ".jpg";
        for (int i = 1; i <= 4; i++) {
            String imageText = "";
            if (i == 2) {
                imageFormat = ".png";
            } else {
                imageFormat = ".jpg";
            }

            if (i == 4) {
                tesseract.setLanguage("chi_sim");
            } else {
                tesseract.setLanguage("eng");
            }

            String fileName = "src/main/resources/ImageWithWords" + i;
            File image = new File(fileName + imageFormat);
            try {
                imageText = tesseract.doOCR(image);

            } catch (Exception e) {
                tesseract.setPageSegMode(0);
                try {
                    imageText = tesseract.doOCR(image);
                } catch (Exception e2) {
                    System.out.println("Error");
                }
            }

            result.append(imageText.replaceAll("\n", "").replaceAll("\r", ""));
        }


        char c;
        for (int i = 0; i < result.length(); i++) {
            c = result.charAt(i);                   //get each char from string
            if (!Character.isIdeographic(c)) {
                engResult.append(c);
            } else {
                chiResult.append(c);
            }
        }

        generatePdf("eng-text.pdf", engResult.toString(), "eng");
        generatePdf("chi-text.pdf", chiResult.toString(), "chi");
    }

    private static void generatePdf(String fileName, String content, String lang) {
        try {
            Document document = new Document();
            File pdfFile = new File("pdf/" + fileName + ".pdf");
            pdfFile.getParentFile().mkdirs();
            pdfFile.createNewFile();
            OutputStream outputStream =
                    new FileOutputStream(pdfFile, false);
            PdfWriter.getInstance(document, outputStream);

            document.open();

            Paragraph p = new Paragraph();
            Font blue = FontFactory.getFont(FontFactory.COURIER, 16, BaseColor.BLUE);
            Font black = FontFactory.getFont(FontFactory.COURIER, 16, BaseColor.BLACK);
            if (lang.equals("chi")) {
                black = FontFactory.getFont(AsianFontMapper.ChineseSimplifiedFont, AsianFontMapper.ChineseSimplifiedEncoding_H, BaseFont.NOT_EMBEDDED);
            }
            for (char c : content.toCharArray()) {
                if (c == 'o' || c == 'O') {
                    p.add(new Chunk(String.valueOf(c), blue));
                } else {
                    p.add(new Chunk(String.valueOf(c), black));
                }
            }

            document.add(p);

            document.close();
            outputStream.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private static Drive getDriveService() throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static void uploadToGoogleDrive() throws Exception {
        Drive service = getDriveService();

        String imageFormat = ".jpg";
        for (int i = 1; i <= 4; i++) {
            if (i == 2) {
                imageFormat = ".png";
            } else {
                imageFormat = ".jpg";
            }

            File filePath = new File("src/main/resources/ImageWithWords" + i + imageFormat);
            if (checkFileExist(filePath.getName())) {
                continue;
            }

            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(filePath.getName());
            fileMetadata.setParents(Collections.singletonList(getParentFolder()));

            FileContent mediaContent = new FileContent("image/jpeg", filePath);
            try {
                com.google.api.services.drive.model.File file = service.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute();
                System.out.println("Success upload file : " + filePath.getName());
            } catch (Exception e) {
                System.err.println("Unable to upload file: " + e.getStackTrace());
                throw e;
            }
        }
    }

    private static String getParentFolder() throws Exception {
        Drive service = getDriveService();
        FileList result = service.files().list()
                .setQ("mimeType='application/vnd.google-apps.folder' and name='SawitPro'")
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name)")
                .setPageToken(null)
                .execute();

        String parentId = "";
        if (result.getFiles().isEmpty()) {
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(FOLDER_NAME);
            fileMetadata.setMimeType("application/vnd.google-apps.folder");
            try {
                com.google.api.services.drive.model.File file = service.files().create(fileMetadata)
                        .setFields("id")
                        .execute();
                System.out.println("Folder ID: " + file.getId());
                return file.getId();
            } catch (Exception e) {
                System.err.println("Unable to create folder: " + e.getStackTrace());
                throw e;
            }
        } else {
            for (com.google.api.services.drive.model.File file : result.getFiles()) {
                if (file.getName().equals(FOLDER_NAME)) {
                    parentId = file.getId();
                }
            }
        }

        return parentId;
    }

    private static boolean checkFileExist(String fileName) throws Exception {
        Drive service = getDriveService();
        FileList result = service.files().list()
                .setQ("mimeType contains 'image/' and parents in '" + getParentFolder() + "' and trashed = false")
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name)")
                .setPageToken(null)
                .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().stream().filter(item -> item.getName().equals(fileName)).count() > 0;
        }

        return false;
    }

    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        InputStream in = Main.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        return credential;
    }

}
