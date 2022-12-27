
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader
{
	private Properties prop;
	
	public ConfigReader(InputStream inputStream)
	{
		prop = new Properties();
		
		try
		{
			prop.load(inputStream);
			;
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public String getProperty(String property)
	{
		return prop.getProperty(property);
	}
	
	public String getProperty(String property, String defaultValue)
	{
		return prop.getProperty(property, defaultValue);
	}
}