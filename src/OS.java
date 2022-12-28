


public class OS
{
	private static final String OS = System.getProperty("os.name");
	
	public static void printOS()
	{
		System.out.println(OS);
	}
	
	public static boolean isWindows()
	{
		return(OS.toLowerCase().indexOf("win") >= 0);
	}
	
	public static boolean isMac()
	{
		return(OS.toLowerCase().indexOf("mac") >= 0);
	}
	
	public static boolean isUnix()
	{
		return(OS.toLowerCase().indexOf("nix") >= 0 || OS.toLowerCase().indexOf("nux") >= 0 || OS.toLowerCase().indexOf("aix") > 0);
	}
	
	public static boolean isSolaris()
	{
		return(OS.toLowerCase().indexOf("sunos") >= 0);
	}
}