package com.dbms.tmsint;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.*;

import org.apache.commons.mail.ByteArrayDataSource;

import javax.activation.DataSource;

import org.apache.commons.mail.*;

import java.lang.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmailUtil {
    public EmailUtil() {
        super();
    }

    private static final String SMTP_HOST_NAME = "172.16.20.82";
    public static final String FILE_SEPERATOR = System.getProperty("file.separator");

    public static void sendEmailWithAttachments(String directoryLocation,
                                                List<String> fileNames) throws EmailException, FileNotFoundException,
                                                                               IOException {
        MultiPartEmail email = new MultiPartEmail();
        email.setHostName(SMTP_HOST_NAME);
        email.addTo("dcaruso@clinicalserver.com", "Donna Caruso");
        email.addTo("caruso.2016@yahoo.com", "Donna Caruso");
        email.addTo("Harish.Pothuri@clinicalserver.com", "Harish Pothuri");
        email.addTo("harish.pvr@gmail.com", "Harish Pothuri");
        email.setFrom("no-reply@tmsint-dbms.com", "TMS Integration");
        email.setSubject("TMS Integration - Import Text Files");
        email.setMsg("Please find the text files that have been generated post integration with TMS.");

        // get your inputstream from your db
        InputStream is = null;
        DataSource source = null;
        for (String file : fileNames) {
            is = new BufferedInputStream(new FileInputStream(directoryLocation + FILE_SEPERATOR + file));
            source = new ByteArrayDataSource(is, "text/plain");
            // add the attachment
            email.attach(source, file, "Import text file");
        }

        // send the email
        email.send();
    }

    public static void main(String[] args) {
        try {
            //  EmailUtil.sendEmailWithAttachments("C:\\Users\\SBG_PC521\\Downloads\\TestImportToText.txt");


            Email email = new SimpleEmail();
            email.setHostName("smtp-relay.cswg.com");
            //            email.setSmtpPort(25);
            //  email.setAuthenticator(new DefaultAuthenticator("harish.pvr@gmail.com", "hariyam1627"));
            //            email.setSSLOnConnect(true);
            email.setFrom("harish.pvr@gmail.com");
            email.setSubject("TestMail");
            email.setMsg("This is a test mail ... :-)");
            email.addTo("harish.pvr@gmail.com");
            email.send();
        } catch (EmailException e) {
            e.printStackTrace();
            //        } catch (FileNotFoundException e) {
            //            e.printStackTrace();
            //        } catch (IOException e) {
            //            e.printStackTrace();
        }
    }
}
