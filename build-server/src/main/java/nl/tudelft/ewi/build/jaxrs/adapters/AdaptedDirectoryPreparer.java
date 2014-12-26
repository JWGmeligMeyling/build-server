package nl.tudelft.ewi.build.jaxrs.adapters;

import java.io.File;
import java.io.PrintWriter;

public abstract class AdaptedDirectoryPreparer {
	
	public abstract void prepareStagingDirectory(File stagingDirectory,
			PrintWriter writer ) throws DirectoryPreparerException;

	public static class DirectoryPreparerException extends Exception {
		
		private static final long serialVersionUID = 8385193788265949861L;

		public DirectoryPreparerException(Throwable t) {
			super(t);
		}
		
	}
	
}
