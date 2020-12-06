package com.isotope11.erlanglauncher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.system.Os;
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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

  private static Context context;
  private static String filesDir;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    context = getApplicationContext();
    filesDir = context.getFilesDir().getAbsolutePath();

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

      this.createErlangRuntimeIntoDataDir(); // Need to make this optional, check if it's there, or something...
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

    public void listFiles() {
      this.doCommand("/system/bin/ls -al " + filesDir +
                     "/erlang/erts-11.0.2/bin");
    }

    public void launchErlangNode() {
      Log.d("Fragment", "launchErlangNode");
      // The HOME environment variable must be set for the Erlang server node
      // to launch, otherwise the launch would fail with the following message:
      //    "error:: erlexec: HOME must be set"
      // with previous versions of the Erlang runtime. This was fixed in
      // Erlang 23 as described here: https://bugs.erlang.org/browse/ERL-476
      //
      // Pass the ERL_ROOTDIR environment variable to set dynamically the
      // absolute path of the Erlang Runtime which is configured in a
      // different location for multiple users on Android. The change has
      // been discussed here: https://github.com/erlang/otp/pull/2863
      String[] envp = { "HOME=" + filesDir,
                        "ERL_ROOTDIR=" + filesDir + "/erlang" };

      // Launch the Erlang server node locally.
      this.doCommand("erlang/bin/erl -detached -name server@127.0.0.1 " +
                     // "-sname server@localhost" could be used instead, or even "-sname server"
                     // Remove the -detached option to get the error messages in the log, if any
                     "-setcookie cookie " + // the "cookie" shared among all nodes
                     "-pa files/ " + // <= the directory where the hello_jinterface.beam is found
                     "-s hello_jinterface",
                     envp,
                     // The working directory used when launching the command
                     new File(filesDir + "/"),
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

    protected void createErlangRuntimeIntoDataDir() {
      Log.d("Fragment", "createErlangRuntimeIntoDataDir start");

      ApplicationInfo info = context.getApplicationInfo();

      File filesMappingFile = new File(info.nativeLibraryDir, "libmappings.so");
      if (!filesMappingFile.exists()) {
        Log.e("Fragment", "No file mapping at " +
              filesMappingFile.getAbsolutePath());
        return;
      }

      Log.d("Fragment", "Create the Erlang Runtime file structure");
      try {
        BufferedReader reader =
          new BufferedReader(new FileReader(filesMappingFile));
        String line;
        while ((line = reader.readLine()) != null) {
          String[] parts = line.split("←");
          if (parts.length != 2) {
            Log.e("Fragment", "Malformed line " + line + " in " +
                  filesMappingFile.getAbsolutePath());
            continue;
          }

          String oldPath = info.nativeLibraryDir + "/" + parts[0];
          String newPath = filesDir + "/" + parts[1];

          File directory = new File(newPath).getParentFile();
          if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new RuntimeException("Unable to create directory: " + directory.getAbsolutePath());
          }

          Log.d("Fragment", "About to setup link: " + oldPath + " ← " + newPath);
          new File(newPath).delete();
          createSymLink(oldPath, newPath);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      Log.d("Fragment", "createErlangRuntimeIntoDataDir done");
    }

    protected void copyErlangServerCode() {
      InputStream erlangServerCodeInputStream;
      FileOutputStream out;
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

  // Support the creation of symlinks before Android 5.0 Lollipop
  static void createSymLink(String originalFilePath, String newPath) {
    try {
      if (Build.VERSION.SDK_INT >= 21) { // VERSION_CODES.LOLLIPOP
        Os.symlink(originalFilePath, newPath);
        return;
      }
      final Class<?> libcore = Class.forName("libcore.io.Libcore");
      final java.lang.reflect.Field fOs = libcore.getDeclaredField("os");
      fOs.setAccessible(true);
      final Object os = fOs.get(null);
      final java.lang.reflect.Method method = os.getClass().getMethod("symlink", String.class, String.class);
      method.invoke(os, originalFilePath, newPath);
    } catch (Exception e) {
      e.printStackTrace();
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
