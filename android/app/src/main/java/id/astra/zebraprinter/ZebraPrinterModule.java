package id.astra.zebraprinter;

import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.device.ProgressMonitor;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.SGD;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class ZebraPrinterModule extends ReactContextBaseJavaModule {

    ZebraPrinterModule(ReactApplicationContext context) {
        super(context);
    }

    final String TAG = "PRINT ZEBRA";

    @Override
    public String getName() {
        return "ZebraPrinterModule";
    }

    @ReactMethod
    public void discoveryPrinter(@NonNull Callback cb) {
        try {
            ArrayList<Map<String, String>> foundPrinterList = new ArrayList<Map<String, String>>();
            BluetoothDiscoverer.findPrinters(getReactApplicationContext(), new DiscoveryHandler() {
                @Override
                public void foundPrinter(DiscoveredPrinter printer) {
                    Map<String, String> foundPrinter = new HashMap<>();
                    foundPrinter.put("address", printer.address);
                    foundPrinter.put("friendlyName", ((DiscoveredPrinterBluetooth) printer).friendlyName);
                    foundPrinterList.add(foundPrinter);
                }

                @Override
                public void discoveryFinished() {
                    if (!foundPrinterList.isEmpty()) {
                        List<JSONObject> jsonObj = new ArrayList<JSONObject>();

                        for (Map<String, String> data : foundPrinterList) {
                            jsonObj.add(new JSONObject(data));
                        }

                        JSONArray foundPrinterJson = new JSONArray(jsonObj);
                        cb.invoke(null, foundPrinterJson.toString());
                    } else {
                        cb.invoke("Printer not found", null);
                    }

                }

                @Override
                public void discoveryError(String s) {
                    cb.invoke(s, null);
                }
            });
        } catch (ConnectionException e) {
            // Do something
            Log.e("DISCOVERY", "testModule: " + e.getMessage());
            cb.invoke(e.getMessage(), null);
        }
    }

    @ReactMethod
    public void printPdf(String pdfUri, String address, Callback callback) {
        Connection connection = new BluetoothConnection(address);
        File tempFilePdf = getFileFromUri(Uri.parse(pdfUri));
        try {
            connection.open();
            // Verify Printer Supports PDF
            if (zebraPrinterSupportsPDF(connection)) {
                if (connection.isConnected()) {
                    try {
                        // Get Instance of Printer
                        ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);

                        // Verify Printer Status is Ready
                        PrinterStatus printerStatus = printer.getCurrentStatus();
                        Log.wtf("PRINTER STATUS", printerStatus.toString());
                        if (printerStatus.isReadyToPrint) {
                            // Send the data to printer as a byte array.
                            if (tempFilePdf != null) {
                                printer.sendFileContents(tempFilePdf.getAbsolutePath(), new ProgressMonitor() {
                                    @Override
                                    public void updateProgress(int write, int total) {
                                        float rawProgress = (float) (write * 100 / total);
                                        int progress = (int) Math.round(rawProgress);
                                        if (progress == 100) {
                                            callback.invoke("Print Finish");
                                        }
                                    }
                                });
                            }

                            // Make sure the data got to the printer before closing the connection
                            Thread.sleep(500);
                        } else {
                            callback.invoke("Printer is not ready!");
                            if (printerStatus.isPaused) {
                                Log.e(TAG, "Printer paused");
                            } else if (printerStatus.isHeadOpen) {
                                Log.e(TAG, "Printer head open");
                            } else if (printerStatus.isPaperOut) {
                                Log.e(TAG, "Printer is out of paper");
                            } else {
                                Log.e(TAG, "Unknown error occurred");
                            }
                        }
                    } catch (ConnectionException | InterruptedException |
                             ZebraPrinterLanguageUnknownException e) {
                        // Pass Error Up
                        Log.e(TAG, e.getMessage());
                        callback.invoke(e.getMessage());
                    } finally {
                        try {
                            // Close Connections
                            connection.close();
                        } catch (ConnectionException e) {
                            callback.invoke(e.getMessage());
                        }
                    }
                }
            } else {
                Log.e(TAG, "Printer does not support PDF Printing");
                // Close Connection
                callback.invoke("Printer does not support PDF Printing");
                connection.close();
            }

        } catch (ConnectionException e) {
            callback.invoke(e.getMessage());
        }

    }

    @Nullable
    File getFileFromUri(@Nullable Uri contentUri) {
        // Get Input Stream && Init File
        File pdfFile = null;
        try {
            if (contentUri != null) {
                InputStream inputStream = getReactApplicationContext().getContentResolver().openInputStream(contentUri);
                if (inputStream != null) {
                    try {
                        pdfFile = File.createTempFile(
                                "TempFilePdf",
                                ".pdf",
                                getReactApplicationContext().getCacheDir()
                        );
                        FileOutputStream output = new FileOutputStream(pdfFile);
                        byte[] buffer = new byte[4 * 1024]; // or other buffer size
                        int read = 0;
                        while ((read = inputStream.read(buffer)) > 0) {
                            output.write(buffer, 0, read);
                        }
                        output.flush();
                        output.close();

                    } catch (Exception e) {
                        inputStream.close();
                    } finally {
                        inputStream.close();
                    }
                }
            }

        } catch (Exception e) {
            return null;
        }
        return pdfFile;
    }

    private Boolean zebraPrinterSupportsPDF(Connection connection) {
        try {
            // Use SGD command to check if apl.enable returns "pdf"
            // SGD.SET("apl.enable","pdf", connection)
            String printerInfo = SGD.GET("apl.enable", connection);
            return Objects.equals(printerInfo, "pdf");
        } catch (ConnectionException e) {
            return false;
        }
    }


    @ReactMethod
    public void zsdkWriteBluetooth(String macAddress, String zpl) {
        Log.d("ZSDKModule", "Going to write via Bluetooth with MAC address: " + macAddress
                + " and zpl: " + zpl);

        Connection printerConnection = null;
        ZebraPrinter printer = null;

        printerConnection = new BluetoothConnection(macAddress);

        try {
            printerConnection.open();

            if (printerConnection.isConnected()) {
                printer = ZebraPrinterFactory.getInstance(printerConnection);
                PrinterLanguage printerLanguage = printer.getPrinterControlLanguage();
                byte[] testLabel = getTestLabel(printerLanguage);
                printerConnection.write(testLabel);
            }
        } catch (ConnectionException | ZebraPrinterLanguageUnknownException e) {
            // Do something
        } finally {
            try {
                if (printerConnection != null) {
                    printerConnection.close();
                }
            } catch (ConnectionException ex) {
                // Do something
            }
        }
    }

    /*
     * Returns the command for a test label depending on the printer control language
     * The test label is a box with the word "TEST" inside of it
     *
     * _________________________
     * |                       |
     * |                       |
     * |        TEST           |
     * |                       |
     * |                       |
     * |_______________________|
     *
     *
     */
    private byte[] getTestLabel(PrinterLanguage printerLanguage) {
        byte[] testLabel = null;
        if (printerLanguage == PrinterLanguage.ZPL) {
            testLabel = "^XA^FO17,16^GB379,371,8^FS^FT65,255^A0N,135,134^FDTEST^FS^XZ".getBytes();
        } else if (printerLanguage == PrinterLanguage.CPCL || printerLanguage == PrinterLanguage.LINE_PRINT) {
            String cpclConfigLabel = "! 0 200 200 406 1\r\n" + "ON-FEED IGNORE\r\n" + "BOX 20 20 380 380 8\r\n" + "T 0 6 137 177 TEST\r\n" + "PRINT\r\n";
            testLabel = cpclConfigLabel.getBytes();
        }
        return testLabel;
    }

    @ReactMethod
    public void zsdkPrinterDiscoveryBluetooth(Callback callback) {
        try {
            BluetoothDiscoverer.findPrinters(getReactApplicationContext(), new DiscoveryResult(callback));
        } catch (ConnectionException e) {
            // Do something
        } finally {
            // Do something
        }
    }

    // Implementation to DiscoveryHandler
    public class DiscoveryResult implements DiscoveryHandler {

        protected Callback callback = null;
        protected ArrayList<Map<String, String>> foundPrinterList;

        public DiscoveryResult(Callback callback) {
            super();
            this.callback = callback;
            foundPrinterList = new ArrayList<Map<String, String>>();
        }

        @Override
        public void foundPrinter(final DiscoveredPrinter printer) {
            DiscoveredPrinter dp = printer;
            Map<String, String> foundPrinter = new HashMap<>();
            foundPrinter.put("address", printer.address);
            foundPrinter.put("friendlyName", ((DiscoveredPrinterBluetooth) printer).friendlyName);
            foundPrinterList.add(foundPrinter);
        }

        @Override
        public void discoveryFinished() {

            // Convert the foundPrinterList into JSON string
            List<JSONObject> jsonObj = new ArrayList<JSONObject>();

            for (Map<String, String> data : foundPrinterList) {
                jsonObj.add(new JSONObject(data));
            }

            JSONArray foundPrinterJson = new JSONArray(jsonObj);

            Log.d("ZSDKModule", "Found printers are: " + foundPrinterJson.toString());

            // Invoke the callback in React Native
            callback.invoke(null, foundPrinterJson.toString());
        }

        @Override
        public void discoveryError(String message) {
            // To do
        }
    }
}