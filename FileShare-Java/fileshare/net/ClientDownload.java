package fileshare.net;

import fileshare.FileShare;
import fileshare.gui.FormMain;
import fileshare.settings.OneFile;
import fileshare.settings.Settings;
import java.io.*;
import java.net.Socket;

/**
 * Download file from server.
 *
 * @author Jakub Trmota | Forrest79
 */
public class ClientDownload implements Runnable, ITransfer {
	/**
	 * Downloading file.
	 */
	private OneFile file = null;

	/**
	 * Is download canceled?
	 */
	private boolean cancel = false;

	/**
	 * Main form.
	 */
	private FormMain formMain = null;

	/**
	 * Initialize download file.
	 *
	 * @param file
	 * @param formMain
	 */
	public ClientDownload(OneFile file, FormMain formMain) {
		this.file = file;
		this.formMain = formMain;
	}

	/**
	 * Main thread function.
	 */
	@Override
	public void run() {
		try {
			Socket socket = new Socket(file.getUser().getAddress(), file.getUser().getPort());

			String fileName = Transfers.getFreeDownloadFilename(Settings.getSettings().getDownloadDir() + FileShare.SLASH + file.getName());
      File downloadFile = new File(fileName);
			byte[] data = new byte[16384];
			try {
				BufferedInputStream inputData = new BufferedInputStream(socket.getInputStream());
				BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				BufferedOutputStream fileOutput = new BufferedOutputStream(new FileOutputStream(downloadFile));

				output.write("GET " + file.getPath() + "\n");
				output.write(Settings.encode(file.getUser().getPassword()) + "\n");
				output.flush();

				long fileSize = 0;

				long dataSize = 0;
				boolean readResponse = true;
				boolean error = false;
				int i;
				while(((i = inputData.read(data)) != -1) && !cancel) {
					if (readResponse) {
						readResponse = false;
						if ((data[0] == 79) && (data[1] == 75)) {
							int eol = data[2];

							String size = "";
							int x;
							for (x = 3; x < i; x++) {
								if (data[x] == eol) {
									break;
								}

								size += (char) data[x];
							}

							fileSize = Long.parseLong(size);

							Transfers.getTransfers().addTransfer(Transfers.DOWNLOAD, file.getName(), fileSize, this);

							fileOutput.write(data, x + 1, i - x - 1);
							dataSize += i - x - 1;

							continue;
						} else {
							cancel = true;
							error = true;
							break;
						}
					}

					fileOutput.write(data, 0, i);
					dataSize += i;

					Transfers.getTransfers().updateCompleted(this, (int) (((double) dataSize / (double) fileSize) * 100));
				}
				Transfers.getTransfers().done(this);

				if (dataSize < fileSize) {
					cancel = true;
				}

				fileOutput.close();
				inputData.close();

				if (cancel) {
					downloadFile.delete();
				}

				if (error) {
					formMain.showErrorDialog("Transport error: " + file.getUser().getName(), "No file was send...");
				}
			} catch(Exception ex) {
				formMain.showErrorDialog("Transport error: " + file.getUser().getName(), ex.getMessage());
			}
		} catch (Exception ex) {
			formMain.showErrorDialog("Transport error: " + file.getUser().getName(), ex.getMessage());
		}
	}

	/**
	 * Cancel download.
	 */
	@Override
	public void cancel() {
		cancel = true;
	}
}
