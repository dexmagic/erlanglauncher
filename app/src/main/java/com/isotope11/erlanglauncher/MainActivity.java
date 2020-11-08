package com.isotope11.erlanglauncher;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangDecodeException;
import com.ericsson.otp.erlang.OtpErlangExit;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.ericsson.otp.erlang.OtpMbox;
import com.ericsson.otp.erlang.OtpNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

  private static Context context;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    MainActivity.context = getApplicationContext();

    setContentView(R.layout.activity_main);

    if (savedInstanceState == null) {
      getSupportFragmentManager().beginTransaction()
              .add(R.id.container, new PlaceholderFragment())
              .commit();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {

    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();
    if (id == R.id.action_settings) {
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  /**
   * A placeholder fragment containing a simple view.
   */
  public static class PlaceholderFragment extends Fragment {
    TextView mHello;

    public PlaceholderFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
      View rootView = inflater.inflate(R.layout.fragment_main, container, false);

      this.mHello = (TextView) rootView.findViewById(R.id.helloWorld);

      this.listFiles();
      this.copyErlangIntoDataDir(); // Need to make this optional, check if it's there, or something...
      this.makeExecutable("erlang/bin/erl");
      this.makeExecutable("erlang/erts-11.0.2/bin/erlexec");
      this.makeExecutable("erlang/erts-11.0.2/bin/beam.smp");
      this.makeExecutable("erlang/erts-11.0.2/bin/erl_child_setup");
      this.makeExecutable("erlang/erts-11.0.2/bin/epmd");
      this.makeExecutable("erlang/erts-11.0.2/bin/inet_gethost");
      // + other executables in erts-X.Y.Z/bin potentially such as erl, erlc...

      this.listFiles();
      this.copyErlangServerCode();
      this.launchErlangNode(); // This command is also launching the Epmd daemon
      try {
        // Wait 2 seconds for the server node to finish launching.
        // TODO: code should be improved to avoid this random wait time
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      this.listProcesses();
      JInterfaceTester task = new JInterfaceTester();
      task.execute();

      this.mHello.setText("All good...");

      return rootView;
    }

    public void makeExecutable(String path) {
      this.doCommand("/system/bin/chmod 777 /data/data/com.isotope11.erlanglauncher/files/" + path);
    }

    public void listFiles() {
      this.doCommand("/system/bin/ls -al /data/data/com.isotope11.erlanglauncher/files/erlang/erts-11.0.2/bin");
    }

    public void launchErlangNode() {
      // The HOME environment variable must be set for the Erlang server node to launch,
      // otherwise the launch fails with the following message:
      //    "error:: erlexec: HOME must be set"
      // There is a bug on this topic here: https://bugs.erlang.org/browse/ERL-476
      String[] envp = { "HOME=/data/data/com.isotope11.erlanglauncher" };

      // Launch the Erlang server node locally.
      this.doCommand("files/erlang/bin/erl -detached -name server@127.0.0.1 " +
                     // "-sname server@localhost" could be used instead, or even "-sname server"
                     // Remove the -detached option to get the error messages in the log, if any
                     "-setcookie cookie " + // the "cookie" shared among all nodes
                     "-pa files/ " + // <= the directory where the hello_jinterface.beam is found
                     "-s hello_jinterface",
                     envp,
                     // The working directory used when launching the command
                     new File("/data/data/com.isotope11.erlanglauncher/"),
                     false); // Don't wait for the command to finish)
    }

    public void listProcesses() {
      this.doCommand("/system/bin/ps");
    }

    public void doCommand(String command) {
        this.doCommand(command, null, null, true);
    }

    public void doCommand(String command, String[] envp, File dir, boolean wait) {
      try {
        // Executes the command.
        Process process = Runtime.getRuntime().exec(command, envp, dir);

        // Reads stdout.
        // NOTE: You can write to stdin of the command using
        //       process.getOutputStream().
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        int read;
        char[] buffer = new char[4096];
        StringBuffer output = new StringBuffer();
        while ((read = reader.read(buffer)) > 0) {
          output.append(buffer, 0, read);
        }
        reader.close();

        if (wait) {
          // Waits for the command to finish.
          process.waitFor();
        }

        // Reads stderr.
        reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()));

        StringBuffer error = new StringBuffer();
        while ((read = reader.read(buffer)) > 0) {
          error.append(buffer, 0, read);
        }
        reader.close();

        // Send stdout and stderr to the log
        Log.d("Fragment", output.toString());
        Log.d("Fragment error:", error.toString());

      } catch (IOException e) {
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    protected void copyErlangIntoDataDir() {
      Log.d("Fragment", "copyErlangIntoDataDir start");

      InputStream erlangZipFileInputStream = null;
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            Build.SUPPORTED_ABIS[0].equals("arm64-v8a")) {
          // Use the 64-bit version of the Erlang runtime on compatible 64-bit ARM-based devices
          Log.d("Fragment", "64-bit version");
          erlangZipFileInputStream = context.getAssets().open("erlang_23.0.2_android21_arm64.zip");
        } else {
          // Use the 32-bit version of the Erlang runtime otherwise
          Log.d("Fragment", "32-bit version");
          erlangZipFileInputStream = context.getAssets().open("erlang_23.0.2_androideabi16_arm.zip");
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      Decompress unzipper = new Decompress(erlangZipFileInputStream, "/data/data/com.isotope11.erlanglauncher/files/");
      unzipper.unzip();

      Log.d("Fragment", "copyErlangIntoDataDir done");
    }

    protected void copyErlangServerCode() {
      InputStream erlangServerCodeInputStream = null;
      FileOutputStream out = null;
      try {
        erlangServerCodeInputStream = context.getAssets().open("hello_jinterface.beam");

        out = context.openFileOutput("hello_jinterface.beam", MODE_PRIVATE);
        int read;
        byte[] buffer = new byte[8192];
        while ((read = erlangServerCodeInputStream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        erlangServerCodeInputStream.close();
        out.flush();
        out.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class JInterfaceTester extends AsyncTask<Object, Void, String>{
    @Override
    protected String doInBackground(Object... arg0) {
      testJInterface();
      return "k...";
    }

    public void testJInterface(){
      // Name of the Erlang server node running locally, launched from within this same app
      String server = "server@127.0.0.1"; // or "server@localhost"

      OtpNode self = null;
      OtpMbox mbox = null;
      try {
        self = new OtpNode("mynode",  // or "mynode@127.0.0.1" or "mynode@localhost", all work
                           "cookie"); // the "cookie" shared among all nodes
        mbox = self.createMbox("facserver");

        if (self.ping(server, 2000)) {
          System.out.println("remote is up");
        } else {
          System.out.println("remote is not up");
          return;
        }
      } catch (IOException e1) {
        e1.printStackTrace();
      }

      OtpErlangObject[] msg = new OtpErlangObject[2];
      msg[0] = mbox.self();
      msg[1] = new OtpErlangAtom("ping");
      OtpErlangTuple tuple = new OtpErlangTuple(msg);
      mbox.send("pong", server, tuple);

      while (true)
        try {
          OtpErlangObject robj = mbox.receive();
          OtpErlangTuple rtuple = (OtpErlangTuple) robj;
          OtpErlangPid fromPid = (OtpErlangPid) (rtuple.elementAt(0));
          OtpErlangObject rmsg = rtuple.elementAt(1);

          System.out.println("Message: " + rmsg + " received from:  "
                  + fromPid.toString());

          OtpErlangAtom ok = new OtpErlangAtom("stop");
          mbox.send(fromPid, ok);
          break;

        } catch (OtpErlangExit e) {
          e.printStackTrace();
          break;
        } catch (OtpErlangDecodeException e) {
          e.printStackTrace();
        }
    }

  }
}

class Decompress {
  private InputStream zip;
  private String loc;

  public Decompress(InputStream zipFileInputStream, String location) {
    zip = zipFileInputStream;
    loc = location;

    dirChecker("");
  }

  public void unzip() {
    try {
      ZipInputStream zin = new ZipInputStream(zip);
      ZipEntry ze = null;
      while ((ze = zin.getNextEntry()) != null) {
        Log.d("Fragment", "Unzipping " + ze.getName());

        if (ze.isDirectory()) {
          dirChecker(ze.getName());
        } else {
          FileOutputStream fout = new FileOutputStream(loc + ze.getName());
          int read;
          byte[] buffer = new byte[8192];
          while ((read = zin.read(buffer)) > 0) {
	      fout.write(buffer, 0, read);
          }

          zin.closeEntry();
          fout.close();
        }

      }
      zin.close();
    } catch (Exception e) {
      Log.e("Fragment", "unzip", e);
    }

  }

  private void dirChecker(String dir) {
    File f = new File(loc + dir);

    if (!f.isDirectory()) {
      f.mkdirs();
    }
  }
}
