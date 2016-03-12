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
import java.util.HashMap;
import java.util.Map;
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
		private int filesRenamed;

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
				System.out.println(String.format("%s: processed %d dirs, %d files [%d renamed]", rootPath, dirCounter, fileCounter, filesRenamed));
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
					printLine(prefix + renameToLatin(entry));
					fileCounter++;
				}
			}
		}
		
		private String renameToLatin(Path origPath) throws IOException {
			String origName = origPath.getFileName().toString();
			
			StringBuilder sb = new StringBuilder();
			for (Character c : origName.toCharArray()) {
				sb.append(LatinConvertor.INSTANCE.convert(c));
			}
			String newName = sb.toString();
			if (newName.equals(origName)) {
				return origName;
			}
			
//			System.out.println(String.format("%s -> %s", origName, sb.toString()));
			Files.move(origPath, origPath.getParent().resolve(newName));
			
			filesRenamed++;
			return newName;
		}

		private void printLine(String line) throws IOException {
//			System.out.println(line);
			writer.write(line + "\n");
		}
	}
	
	public static class LatinConvertor {
		public static final LatinConvertor INSTANCE = new LatinConvertor();
		public static final char[][] map = {
				{'à', 'a'},
				{'á', 'b'},
				{'â', 'v'},
				{'ã', 'g'},
				{'ä', 'd'},
				{'å', 'e'},
				{'æ', 'j'},
				{'ç', 'z'},
				{'è', 'i'},
				{'é', 'i'},
				{'ê', 'k'},
				{'ë', 'l'},
				{'ì', 'm'},
				{'í', 'n'},
				{'î', 'o'},
				{'ï', 'p'},
				{'ð', 'r'},
				{'ñ', 's'},
				{'ò', 't'},
				{'ó', 'u'},
				{'ô', 'f'},
				{'õ', 'h'},
				{'ö', 'c'},
				{'÷', '4'},
				{'ø', 's'},
				{'ù', 's'},
				{'ú', '`'},
				{'û', 'y'},
				{'ü', '`'},
				{'ý', 'e'},
				{'þ', 'u'},
				{'ÿ', 'a'},
				};
		
		public static final Map<Character, Character> mapmap = new HashMap<>();
		static {
			final int cyrrCapShift = 'Á' - 'á';
			final int latCapShift = 'D' - 'd';
			for (int i = 0; i < map.length; i++) {
				mapmap.put(map[i][0], map[i][1]);
				
				//add CAPITALS
				mapmap.put(Character.valueOf((char)(map[i][0] + cyrrCapShift)), Character.valueOf((char)(map[i][1] + latCapShift)));
			}
		}
		
		public Character convert(final Character c) {
			Character res = c;
			
			Character repl = mapmap.get(c);
			if (repl != null) {
				res = repl;
			}
			return res;
		}
	}
}
