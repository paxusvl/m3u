package m3u;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class M3u {
	private static final Filter<Path> DIR_FILTER = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.toFile().isDirectory();
		}
	};
	private static final Filter<Path> MP3_FILTER = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.toFile().getName().endsWith("mp3");
		}
	};

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.out.println("No args provided.\n" + "Usage: \n"
					+ "\tm3u rootDir");
			System.exit(1);
		}
		
		
		final ExecutorService pool = Executors.newFixedThreadPool(4);
		submitSubFolders(args[0], pool);
		pool.shutdown();
	}
	
	private static void submitSubFolders(final String dir, final ExecutorService pool) throws IOException {
		Path rootPath = null;
		try {
			rootPath = resolvePath(dir);
		} catch (FileNotFoundException fnfe) {
			System.out.println(dir + " does not exist");
			System.exit(1);
		}
		
		try (final DirectoryStream<Path> stream = Files.newDirectoryStream(rootPath, DIR_FILTER)) {
			for (Path entry: stream) {
				pool.submit(new Producer(entry.toFile().getAbsolutePath()));
			}
		}
	}
	
	public static Path resolvePath(String dir) throws FileNotFoundException {
		Path p = FileSystems.getDefault().getPath(dir);
		if (!p.toFile().exists()) {
			throw new FileNotFoundException();
		}	
		return p;
	}

	private static class Producer implements Runnable {
		private final Path rootPath;
		private final FileWriter writer;
		private int dirCounter;
		private int fileCounter;

		public Producer(String dir) throws IOException, FileNotFoundException {
			rootPath = resolvePath(dir);
			
			String filename = rootPath.getFileName() + ".m3u";
			File file = FileSystems.getDefault().getPath(
					rootPath.toAbsolutePath().toString(), filename)
					.toFile();
			writer = new FileWriter(file, false);
//			System.out.println("initialized: " + file);
		}

		@Override
		public void run() {
			try {
				processDir(rootPath, "");
				System.out.println(String.format("%s: processed %d dirs, %d files", rootPath, dirCounter, fileCounter));
			} catch (IOException e) {
				System.out.println(rootPath + ": failure");
				e.printStackTrace();
			} finally {
				try {
					writer.flush();
					writer.close();
				} catch (IOException e) {}
			}
		}
		
		private void processDir(Path p, String prefix) throws IOException {
			dirCounter++;

			//iterate sub-dirs first
			try (final DirectoryStream<Path> stream = Files.newDirectoryStream(p, DIR_FILTER)) {
				for (Path entry: stream) {
					processDir(entry, prefix + entry.getFileName() + "\\");
				}
			}
			
			//iterate files
			try (final DirectoryStream<Path> stream = Files.newDirectoryStream(p, MP3_FILTER)) {
				for (Path entry: stream) {
					fileCounter++;
					printLine(prefix + entry.getFileName());
				}
			}
			
		}
		
		private void printLine(String line) throws IOException {
//			System.out.println(line);
			writer.write(line + "\n");
		}
	}
}
