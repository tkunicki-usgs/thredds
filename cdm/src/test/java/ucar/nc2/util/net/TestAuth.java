package ucar.nc2.util.net;

import junit.framework.TestCase;

import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.CredentialsProvider;
import org.apache.commons.httpclient.auth.CredentialsNotAvailableException;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.Credentials;
import org.junit.Test;

import java.io.*;
import java.util.List;
import java.io.Serializable;

public class TestAuth extends ucar.nc2.util.TestCommon
{
    // TODO: add proxy and digest tests

    static final String CLIENTKEY = "client.crt";
    static final String CLIENTPWD = "changeit";

    static final String DATAPATH = "cdm/src/test/data";

    //////////////////////////////////////////////////
    // Provide a non-interactive CredentialsProvider to hold
    // the user+pwd; used in several places
    static class BasicProvider implements CredentialsProvider, Serializable
    {
        String username = null; String password = null;

        public BasicProvider(String username, String password)
        {
	    this.username = username;
	    this.password = password;
        }     

	// Credentials Provider Interface
        public Credentials
        getCredentials(AuthScheme authscheme, String host, int port, boolean isproxy)
            throws CredentialsNotAvailableException
            {return new UsernamePasswordCredentials(username,password);}

        // Serializable Interface
        private void writeObject(java.io.ObjectOutputStream oos)
            throws IOException
        {oos.writeObject(this.username);oos.writeObject(this.password);}

        private void readObject(java.io.ObjectInputStream ois)
            throws IOException, ClassNotFoundException
        {
	    this.username = (String)ois.readObject();
            this.password = (String)ois.readObject();
        }
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    // Define the test sets

    int passcount = 0;
    int xfailcount = 0;
    int failcount = 0;
    boolean verbose = true;
    boolean pass = false;

    String datadir = null;
    String threddsroot = null;

    public TestAuth(String name, String testdir)
    {
        super(name);
	setTitle("DAP Authorization tests");
    }

    public TestAuth(String name)
    {
        this(name, null);
    }


    public void
    testSSH() throws Exception
    {
        String[] sshurls = {
            "https://motherlode.ucar.edu:8443/dts/b31.dds"
        };

        System.out.println("*** Testing: Simple Https");
        for(String url: sshurls) {
            System.out.println("*** URL: " + url);
            HTTPSession session = new HTTPSession(url);
            HTTPMethod method = HTTPMethod.Get(session);
            int status = method.execute();
            System.out.printf("\tstatus code = %d\n", status);
            pass = (status == 200);
            junit.framework.Assert.assertTrue("testSSH", pass);
        }
    }

    //////////////////////////////////////////////////
    static class AuthDataBasic
    {
        String url;
        String user;
        String password;
        public AuthDataBasic(String url, String usr, String pwd)
        {
            this.url = url;
            this.user = usr;
            this.password = pwd;
        }
    }

    @Test
    public void
    testBasic() throws Exception
    {
        AuthDataBasic[] basictests = {
            new AuthDataBasic("http://motherlode.ucar.edu/thredds/dodsC/restrict/testdata/testData.nc.html","tiggeUser","tigge")
        };

        System.out.println("*** Testing: Http Basic Password Authorization");

        for(AuthDataBasic data: basictests) {
            CredentialsProvider provider = new BasicProvider(data.user, data.password);
            System.out.println("*** URL: " + data.url);
            // Test local credentials provider
            HTTPSession session = new HTTPSession(data.url);
            session.setCredentialsProvider(provider);
            HTTPMethod method = HTTPMethod.Get(session);
            int status = method.execute();
            System.out.printf("\tlocal provider: status code = %d\n", status);  System.out.flush();
            session.setCredentialsProvider(null);
            pass = (status == 200 || status == 404); // non-existence is ok
            if(pass) System.out.println("\tLocal test passed");

            if(pass) {
                // Test global credentials provider
                HTTPSession.setGlobalCredentialsProvider(provider);
                session = new HTTPSession(data.url);
                method = HTTPMethod.Get(session);
                status = method.execute();
                System.out.printf("\tglobal provider test: status code = %d\n", status);  System.out.flush();
                pass = (status == 200 || status == 404); // non-existence is ok
                if(pass) System.out.println("\tGlobal test passed");
            }
            if(pass)
                junit.framework.Assert.assertTrue("testBasic", true);
            else
                junit.framework.Assert.assertTrue("testBasic", false);
        }
    }


    @Test
    public boolean
    testKeystore() throws Exception
    {
	String url = "hydro2.unidata.ucar.edu/b31.dds";

	// See if the client keystore exists
	String keystore = threddsRoot + "/" + DATAPATH + "/" + CLIENTKEY;
	File tmp = new File(keystore);
	if(!tmp.exists() || !tmp.canRead())
	    throw new Exception("Cannot read client key: "+keystore);

        System.out.println("*** Testing: Client-side Key based Authorization");
        System.out.println("*** URL: " + url);


        HTTPSession session = new HTTPSession(url);
        CredentialsProvider provider = new HTTPSSLProvider(keystore,CLIENTPWD);
	session.setCredentialsProvider(provider);

        HTTPMethod method = HTTPMethod.Get(session);

        int status = method.execute();
        System.out.printf("Execute: status code = %d\n", status);
        pass = (status == 200);
        return pass;
    }

    @Test
    public void
    testSerialize() throws Exception
    {
        System.out.println("*** Testing: HTTPAuthStore (de-)serialization");

        boolean ok = true;
        CredentialsProvider creds1 = new BasicProvider("p1","pwd");
        CredentialsProvider creds2 = new HTTPSSLProvider("keystore","keystorepwd");
        CredentialsProvider creds3 = new BasicProvider("p3","pwd3");

        // Add some entries to HTTPAuthStore
        HTTPAuthStore.clear();
        HTTPAuthStore.insert(new HTTPAuthStore.Entry(
                HTTPAuthScheme.BASIC,
                "http://ceda.ac.uk/dap/neodc/casix/seawifs_plankton/data/monthly/PSC_monthly_1998.nc.dds",
                creds1)
        );
        HTTPAuthStore.insert(new HTTPAuthStore.Entry(
                HTTPAuthScheme.SSL,
                "http://ceda.ac.uk",
                creds2)
        );
        HTTPAuthStore.insert(new HTTPAuthStore.Entry(
                HTTPAuthScheme.BASIC,
                "http://ceda.ac.uk",
                creds3)
        );

        // Remove any old file
        File target1 = new File("./serial1");
        target1.delete();

        // serialize out
        OutputStream ostream = new FileOutputStream(target1);
        HTTPAuthStore.serialize(ostream, "password1");
        // Read in
        InputStream istream = new FileInputStream(target1);
        List<HTTPAuthStore.Entry> entries = HTTPAuthStore.getDeserializedEntries(istream, "password1");

        // compare
        List<HTTPAuthStore.Entry> rows = HTTPAuthStore.getAllRows();
        for (HTTPAuthStore.Entry row : rows) {
            HTTPAuthStore.Entry match = null;
            for (HTTPAuthStore.Entry e : entries) {
block:          {
                if (!HTTPAuthStore.Entry.identical(row,e)) break block;
                if(match == null)
                    match = e;
                else {System.out.println("ambigous match");  ok=false;}
                }
            }
            if (match == null) {
                System.out.println("no match for: " + row.toString());  ok=false;
            }
        }
        junit.framework.Assert.assertTrue("test(De-)Serialize", ok);
    }

}

