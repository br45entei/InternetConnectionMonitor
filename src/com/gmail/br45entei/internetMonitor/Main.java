package com.gmail.br45entei.internetMonitor;

import com.gmail.br45entei.util.CodeUtil;
import com.gmail.br45entei.util.LogKeeper;
import com.gmail.br45entei.util.SWTUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.wb.swt.SWTResourceManager;

/** @author Brian_Entei */
public class Main {
	
	/** @param args Program command line arguments */
	public static void main(String[] args) {
		Display display = Display.getDefault();
		Shell shell = new Shell(display, SWT.CLOSE | SWT.TITLE | SWT.MIN);
		shell.setSize(450, 320);
		shell.setText("Internet Connection Monitor");
		shell.setImages(SWTUtil.getTitleImages());
		
		final Runnable[] updateUI = new Runnable[1];
		
		shell.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				updateUI[0].run();
			}
		});
		
		Label lblDomainipAddress = new Label(shell, SWT.NONE);
		lblDomainipAddress.setBounds(10, 13, 115, 15);
		lblDomainipAddress.setText("Domain/IP Address:");
		
		final Text txtHost = new Text(shell, SWT.BORDER);
		txtHost.setBounds(131, 10, 182, 21);
		txtHost.setMessage("Hostname/IP (e.g. \"8.8.8.8\")");
		
		final Button btnPort = new Button(shell, SWT.CHECK);
		btnPort.setBounds(319, 13, 50, 16);
		btnPort.setText("Port");
		
		final Spinner spnrPort = new Spinner(shell, SWT.BORDER);
		spnrPort.setMinimum(0);
		spnrPort.setMaximum(65535);
		spnrPort.setSelection(80);
		spnrPort.setBounds(375, 10, 59, 22);
		spnrPort.setEnabled(btnPort.getSelection());
		
		btnPort.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				spnrPort.setEnabled(btnPort.getSelection());
			}
		});
		
		Label lblPingEvery = new Label(shell, SWT.NONE);
		lblPingEvery.setBounds(10, 42, 59, 15);
		lblPingEvery.setText("Ping Every");
		
		final Spinner spnrPingInterval = new Spinner(shell, SWT.BORDER);
		spnrPingInterval.setMaximum(3600);
		spnrPingInterval.setMinimum(5);
		spnrPingInterval.setSelection(30);
		spnrPingInterval.setBounds(75, 39, 50, 22);
		
		Label lblSeconds = new Label(shell, SWT.NONE);
		lblSeconds.setBounds(131, 42, 59, 15);
		lblSeconds.setText("Seconds");
		
		final Button btnStartMonitoring = new Button(shell, SWT.TOGGLE);
		btnStartMonitoring.setBounds(196, 37, 238, 25);
		btnStartMonitoring.setText("Start Monitoring");
		shell.setDefaultButton(btnStartMonitoring);
		
		Label label = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
		label.setBounds(10, 68, 424, 2);
		
		final StyledText stxtOutput = new StyledText(shell, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
		stxtOutput.setEditable(false);
		stxtOutput.setSelectionForeground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
		stxtOutput.setSelectionBackground(SWTResourceManager.getColor(SWT.COLOR_GREEN));
		stxtOutput.setForeground(SWTResourceManager.getColor(SWT.COLOR_GREEN));
		stxtOutput.setBackground(SWTResourceManager.getColor(SWT.COLOR_BLACK));
		stxtOutput.setBounds(10, 76, 424, 205);
		
		final boolean[] currentlyMonitoring = {btnStartMonitoring.getSelection()};
		final long[] pingInterval = {spnrPingInterval.getSelection() * 1000L};
		final long[] lastPingTime = {0L};
		final String[] hostName = {txtHost.getText()};
		final Integer[] port = {btnPort.getSelection() ? Integer.valueOf(spnrPort.getSelection()) : null};
		@SuppressWarnings("resource")
		final LogKeeper pr = new LogKeeper(StandardCharsets.ISO_8859_1, true, 20000);//TODO Add a SimpleDateFormat prefix option that affects the various print... methods in this class
		
		btnStartMonitoring.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean monitoring = btnStartMonitoring.getSelection();
				btnStartMonitoring.setText(monitoring ? "Stop Monitoring" : "Start Monitoring");
				SWTUtil.setEnabled(txtHost, !monitoring);
				SWTUtil.setEnabled(btnPort, !monitoring);
				SWTUtil.setEnabled(spnrPort, !monitoring && btnPort.getSelection());
				SWTUtil.setEnabled(spnrPingInterval, !monitoring);
				
				if(monitoring) {
					pr.clear();
					lastPingTime[0] = 0L;
				}
			}
		});
		
		final Thread pingThread = new Thread(() -> {
			final Function<Entry<String, Integer>, Integer> pingServer = new Function<Entry<String, Integer>, Integer>() {
				@SuppressWarnings("resource")
				@Override
				public Integer apply(Entry<String, Integer> server) {
					String host = server.getKey();
					Integer port = server.getValue();
					
					final InetAddress addr;
					
					try {
						addr = InetAddress.getByName(host);
					} catch(UnknownHostException ex) {
						pr.println(String.format("Unknown hostname: %s", host));
						pr.println();
						return Integer.valueOf(-2);
					}
					
					try {
						pr.println(String.format("Sending Ping Request to host \"%s\"...", host));
						int successValue = 0;
						
						long startTime = System.currentTimeMillis();
						if(addr.isReachable(5000)) {
							long elapsedTime = System.currentTimeMillis() - startTime;
							pr.println(String.format("Received reply in %s ms", Long.valueOf(elapsedTime)));
							successValue++;
							
							if(port != null) {
								SocketAddress endpoint = new InetSocketAddress(addr, port.intValue());
								pr.println(String.format("Attempting to connect to \"%s\"...", endpoint));
								try(Socket socket = new Socket()) {
									socket.connect(endpoint, 2000);
									socket.getInputStream();
									socket.getOutputStream().flush();
									successValue++;
									pr.println(String.format("Successfully connected to \"%s\"!", endpoint));
								} catch(IOException ex) {
									pr.println(String.format("Failed to connect to host \"%s:%s\": %s: %s", host, port, ex.getClass().getName(), ex.getMessage()));
									successValue += 2;
								}
							}
							
						} else {
							pr.println(String.format("Unable to reach host %s ...", host));
						}
						
						return Integer.valueOf(successValue);
					} catch(IOException ex) {
						pr.println(String.format("Failed to ping host \"%s\"...", host));
						return Integer.valueOf(-1);
					} finally {
						pr.println();
					}
				}
			};
			//-2: UnknownHostException
			//-1: Ping Failed
			// 0: Unable to reach host
			// 1: Received Ping Response
			// 2: Successfully opened socket to host
			// 3: Failed to open socket to host
			
			while(true) {
				if(currentlyMonitoring[0]) {
					long now = System.currentTimeMillis();
					if(now - lastPingTime[0] >= pingInterval[0]) {
						lastPingTime[0] = now;
						pingServer.apply(CodeUtil.createReadOnlyEntry(hostName[0], port[0]));
					}
				}
				CodeUtil.sleep(10L);
			}
		}, "InternetPingThread");
		pingThread.setDaemon(true);
		pingThread.start();
		
		updateUI[0] = () -> {
			currentlyMonitoring[0] = btnStartMonitoring.getSelection();
			pingInterval[0] = spnrPingInterval.getSelection() * 1000L;
			hostName[0] = txtHost.getText();
			port[0] = btnPort.getSelection() ? Integer.valueOf(spnrPort.getSelection()) : null;
			
			String text = pr.getText();
			if(!stxtOutput.getText().equals(text)) {
				SWTUtil.setTextFor(stxtOutput, text);
			}
		};
		
		shell.open();
		shell.layout();
		
		while(!shell.isDisposed()) {
			updateUI[0].run();
			if(!display.readAndDispatch()) {
				CodeUtil.sleep(10L);
			}
		}
		
		shell.dispose();
		SWTResourceManager.dispose();
		display.dispose();
	}
}
