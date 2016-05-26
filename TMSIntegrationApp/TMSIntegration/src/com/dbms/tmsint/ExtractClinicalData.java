package com.dbms.tmsint;

import com.dbms.tmsint.pojo.DataLine;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;

import java.sql.ResultSet;
import java.sql.Statement;

import java.sql.Struct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import oracle.jdbc.OracleConnection;

import oracle.sql.ARRAY;
import oracle.sql.ArrayDescriptor;
import oracle.sql.STRUCT;
import oracle.sql.StructDescriptor;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ExtractClinicalData {
    public ExtractClinicalData() {
        super();
    }

    public List<String> extractClinicalData() {
        List<String> messages = new ArrayList<String>();
        List<DataLine> dataLines = null;
        Connection conn = null;
        CallableStatement cstmt = null;
        Statement stmt = null;
        String sqlQuery = null;
        ResultSet rs = null;

        try {
            conn = JDBCUtil.getConnection();

            //       1.) Clear all Data from the USER owned TMSINT_XFER_HTML_EXTRACT staging table
            //               that has Already been picked up for processing by the TMS process (PROCESS_FLAG="Y")
            sqlQuery = "begin TMSINT_XFER_UTILS.CLEAR_PROCESSED_EXTRACT_DATA(); end;";
            cstmt = conn.prepareCall(sqlQuery);
            cstmt.executeUpdate();

            //  2.) Determine WHAT DatafileURLS are applicable to the client at hand...
            sqlQuery =
                "  SELECT c.client_alias,c.client_desc,d.datafile_url,d.url_user_name," +
                "              d.url_password," + "              d.study_name" +
                "       FROM TABLE(tmsint_xfer_utils.query_ora_account())  a," +
                "            TABLE(tmsint_xfer_utils.query_client())       c," +
                "            TABLE(tmsint_xfer_utils.query_datafile())     d" +
                "       WHERE a.client_id = c.client_id" + "         AND c.client_id = d.client_id" +
                "         AND a.active_flag = 'Y'" + "         AND c.active_flag = 'Y'" +
                "         AND d.active_flag = 'Y'";

            stmt = conn.createStatement();
            rs = stmt.executeQuery(sqlQuery);
            while (rs.next()) {
                String sourceUrl = rs.getString("datafile_url");
                String userName = rs.getString("url_user_name");
                String password = rs.getString("url_password");

                if (sourceUrl != null && userName != null && password != null) {
                    dataLines = readDataFromUrl(sourceUrl, userName, password);

                    //  3.) For EACH client datafile record retrieved in the cursor query above, (using your Java magic)
                    //       Connect to the DatafileURL using the URLUserName and URLPassword.
                    //       Each Line of the HTML data file should be written to the HTLM extract staging table
                    //       via the API below:
                    if (dataLines != null && dataLines.size() > 0) {

                        Struct[] dataLineSqlRecList = new Struct[dataLines.size()];
                        for (int i = 0; i < dataLines.size(); i++) {
                            dataLineSqlRecList[i] =
                                conn.createStruct("TMSINT_XFER_HTML_WS_RECORD",
                                                  new Object[] { dataLines.get(i).getUrl(),
                                                                 dataLines.get(i).getText() });
                        }

                        System.out.println("Number of lines to be inserted : " + dataLineSqlRecList.length);

                        Array dataLineSqlTabType =
                            ((OracleConnection) conn).createOracleArray("TMSINT_XFER_HTML_WS_TABLE",
                                                                        dataLineSqlRecList);

                        sqlQuery = "begin TMSINT_XFER_UTILS.INSERT_EXTRACT_BULK(?); end;";
                        cstmt = conn.prepareCall(sqlQuery);
                        cstmt.setArray(1, dataLineSqlTabType);
                        cstmt.executeUpdate();

                    }
                }
            }

            //    4.) After writing all of HTML dataifle content, analyze the HTML Extract table
            //       USER.TMSINT_XFER_HTML_EXTRACT (change syntax where appropriate)
            sqlQuery = "begin TMSINT_XFER_UTILS.ANALYZE_XFER_TABLES(); end;";
            cstmt = conn.prepareCall(sqlQuery);
            cstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            JDBCUtil.closeStatement(cstmt);
            JDBCUtil.closeStatement(stmt);
            JDBCUtil.closeConnection(conn);
        }


        return messages;
    }

    private void ignoreAllTrusts() throws NoSuchAlgorithmException, KeyManagementException {

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }

    private List<DataLine> readDataFromUrl(String sourceUrl, String userName,
                                           String password) throws MalformedURLException, IOException,
                                                                   NoSuchAlgorithmException, KeyManagementException {
        List<DataLine> dataLines = new ArrayList<DataLine>();
        List<String> textLines = new ArrayList<String>();

        //        String authString = userName + ":" + password;
        //        System.out.println("auth string: " + authString);
        //        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        //        String authStringEnc = new String(authEncBytes);
        //        System.out.println("Base64 encoded auth string: " + authStringEnc);
        //
        //        ignoreAllTrusts();
        //        URL url = new URL(sourceUrl);
        //        URLConnection con = url.openConnection();
        //        con.setRequestProperty("Authorization", "Basic " + authStringEnc);

        HttpClient client = new HttpClient(); // Apache's Http client
        Credentials credentials = new UsernamePasswordCredentials(userName, password);

        client.getState().setCredentials(AuthScope.ANY, credentials);
        client.getState().setProxyCredentials(AuthScope.ANY, credentials); // may not be necessary

        client.getParams().setAuthenticationPreemptive(true); // send authentication details in the header

        GetMethod httpget = new GetMethod(sourceUrl);
        int statusCode = client.executeMethod(httpget);
        if (statusCode == HttpStatus.SC_OK) {
            BufferedInputStream reader = new BufferedInputStream(httpget.getResponseBodyAsStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(reader));
            String line = null;
            while ((line = br.readLine()) != null) {
                line = line.replaceAll("[^\\x20-\\x7e]", "");
                line = format(line);
                System.out.println("Formatted xml before split");
                System.out.println("----------------------------");
                System.out.println(line);
                textLines.addAll(Arrays.asList(line.split("\\r\\n|\\n|\\r")));
            }

            if (!textLines.isEmpty()) {
                for (String text : textLines) {
                    // ignore xml declaration lines
                    if (!text.startsWith("<?xml"))
                        dataLines.add(new DataLine(sourceUrl, text));
                }
            }
        }
        return dataLines;
    }


    private String format(String unformattedXml) {
        try {
            final Document document = parseXmlFile(unformattedXml);

            OutputFormat format = new OutputFormat(document);
            format.setLineWidth(65);
            format.setIndenting(true);
            format.setIndent(2);
            Writer out = new StringWriter();
            XMLSerializer serializer = new XMLSerializer(out, format);
            serializer.serialize(document);

            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Document parseXmlFile(String in) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(in));
            return db.parse(is);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        ExtractClinicalData ex = new ExtractClinicalData();
        ex.extractClinicalData();
    }
}
