import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Properties;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONObject;
import org.toilelibre.libe.curl.Curl;

public class Updater
{
	public static void main(String[] args) throws IOException
	{
		new Updater();
	}
	
	private String user, token, zone, zoneID;
	private String[] domains;
	private String[] dns_zone_ids;
	
	private String ip = "";
	private String lastIP = "";
	
	private final String api = "https://api.cloudflare.com/client/v4";
	private final String api_zones = "/zones";
	private final String api_dns_records = "/:ZONEID:/dns_records";
	
	private final String curl = "curl -X :METHOD: :API_REQUEST:";
	private final String curl_suffix = " -H \"X-Auth-Email: :USER:\" -H \"Authorization: Bearer :TOKEN:\" -H \"Content-Type: application/json\"";
	
	private final String[] ipCatchSites = {"https://cloudflare.com/cdn-cgi/trace", "https://api.ipify.org", "http://checkip.dyndns.org"};
	
	public Updater()
	{
		readConfig();
		
		new Timer().scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				ip = getIP();
				if(ip == null)
				{
					System.err.println("[ERROR] <IP> Unable to determin IPv4-Address! Tried following services:");
					for(String service : ipCatchSites)
						System.err.println(service);
					return;
				}
				
				if(ip.equals(lastIP))
					return;
				
				setZoneID(getZones());
				setDNSzones(getDNSrecords());
				
				updateDNSrecords();
				
				lastIP = ip;
			}
		}, 0, 300000);
	}
	
	private void createDNSrecord(String domain)
	{
		String api_request = api + api_zones + api_dns_records.replace(":ZONEID:", zoneID);
		String data = " --data '{\"type\":\"A\",\"name\":\":DOMAIN:\",\"content\":\":IPv4:\",\"ttl\":1,\"proxied\":false}'";
		String cmd = curl.replace(":METHOD:", "POST").replace(":API_REQUEST:", api_request) + curl_suffix.replace(":TOKEN:", token).replace(":USER:", user) + data.replace(":DOMAIN:", domain).replace(":IPv4:", ip);
		
		String answer = runCommand(cmd);
		if(getResponse(answer) != null)
			System.out.println("[INFO] <API> Successfully created dns A record for " + domain + " with IP " + ip);
	}
	
	private void updateDNSrecords()
	{
		for(int i = 0; i < dns_zone_ids.length; i++)
			updateDNSrecord(dns_zone_ids[i], domains[i]);
	}
	
	private void updateDNSrecord(String dns_zone_id, String domain)
	{
		if(dns_zone_id == null)
			createDNSrecord(domain);
		
		String api_request = api + api_zones + api_dns_records.replace(":ZONEID:", zoneID) + "/" + dns_zone_id;
		String data = " --data '{\"type\":\"A\",\"name\":\":DOMAIN:\",\"content\":\":IPv4:\",\"ttl\":1,\"proxied\":false}'";
		String cmd = curl.replace(":METHOD:", "PUT").replace(":API_REQUEST:", api_request) + curl_suffix.replace(":TOKEN:", token).replace(":USER:", user) + data.replace(":DOMAIN:", domain).replace(":IPv4:", ip);
		
		String answer = runCommand(cmd);
		if(getResponse(answer) != null)
			System.out.println("[INFO] <API> Successfully updated dns A record for " + domain + " with IP " + ip);
	}
	
	private JSONArray getDNSrecords()
	{
		String api_request = api + api_zones + api_dns_records.replace(":ZONEID:", zoneID);
		String cmd = curl.replace(":METHOD:", "GET").replace(":API_REQUEST:", api_request) + curl_suffix.replace(":TOKEN:", token).replace(":USER:", user);
		
		String answer = runCommand(cmd);
		return getResult(answer);
	}
	
	private JSONArray getZones()
	{
		String api_request = api + api_zones;
		String cmd = curl.replace(":METHOD:", "GET").replace(":API_REQUEST:", api_request) + curl_suffix.replace(":TOKEN:", token).replace(":USER:", user);
		
		String answer = runCommand(cmd);
		return getResult(answer);
	}
	
	private String getIP()
	{
		return getIP(0);
	}
	
	private String getIP(int tries)
	{
		try
		{
			URL url = new URL(ipCatchSites[tries]);
			URLConnection conn = url.openConnection();
			InputStream is = conn.getInputStream();
			Scanner sc = new Scanner(is).useDelimiter("\\A");
			String response = sc.next();
			
			// special cases: cloudflare & dyndns don't only return ip
			switch(tries)
			{
				case 0:
					String[] lines = response.split("\n");
					for(String line : lines)
						if(line.contains("ip="))
						{
							sc.close();
							is.close();
							if(line.split("=")[1].matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"))
							{
								return line.split("=")[1];
							}
						}
					break;
				
				case 2:
					sc.close();
					is.close();
					if(response.contains("IP Address: "))
						if(response.split("IP Address: ")[1].split("<")[0].matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"))
							return response.split("IP Address: ")[1].split("<")[0];
					break;
				
				default:
					sc.close();
					is.close();
					if(response.matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"))
						return response;
					if(tries >= ipCatchSites.length)
						return null;
					return getIP(tries + 1);
			}
			
			sc.close();
			is.close();
			if(tries >= ipCatchSites.length)
				return null;
			return getIP(tries + 1);
		}
		catch(IOException e)
		{
			
			e.printStackTrace();
		}
		
		if(tries >= ipCatchSites.length)
			return null;
		
		return getIP(tries + 1);
	}
	
	private JSONArray getResult(String raw)
	{
		JSONObject response = new JSONObject(raw);
		
		if(!response.getBoolean("success"))
		{
			handleNotSuccessful(response);
			return null;
		}
		
		JSONArray result = (JSONArray) response.get("result");
		
		return result;
	}
	
	private JSONObject getResponse(String raw)
	{
		JSONObject response = new JSONObject(raw);
		
		if(!response.getBoolean("success"))
		{
			handleNotSuccessful(response);
			return null;
		}
		
		return response;
	}
	
	private void setZoneID(JSONArray result)
	{
		result.forEach(d ->
		{
			JSONObject obj = (JSONObject) d;
			if(obj.getString("name").equals(zone))
				zoneID = obj.getString("id");
		});
	}
	
	private void setDNSzones(JSONArray result)
	{
		result.forEach(d ->
		{
			JSONObject obj = (JSONObject) d;
			for(int i = 0; i < domains.length; i++)
			{
				if(obj.getString("name").equals(domains[i]))
					dns_zone_ids[i] = obj.getString("id");
			}
		});
	}
	
	private void handleNotSuccessful(JSONObject response)
	{
		System.err.println("[ERROR] <API> got response success:false");
		for(int i = 0; i < ((JSONArray) response.get("errors")).length(); i++)
		{
			JSONObject obj = (JSONObject) ((JSONArray) response.get("errors")).get(i);
			if(obj.has("message"))
				System.err.println("[ERROR] <API> " + obj.get("message"));
		}
	}
	
	private String runCommand(String command)
	{
		String output = null;
		
		output = Curl.$(command);
		
		return output;
	}
	
	private void createDefaultConfig()
	{
		Properties prop = new Properties();
		HashMap<String, String> defaults = new HashMap<String, String>();
		defaults.put("user", "your cloudflare login email");
		defaults.put("token", "your api token");
		defaults.put("zone", "e.g. example.com");
		defaults.put("domains", "e.g. www.example.com,example.com,test.example.com");
		prop.putAll(defaults);
		try
		{
			prop.store(new FileOutputStream("config.properties"), null);
		}
		catch(IOException e)
		{
			System.err.println("[ERROR] <CONFIG> Unable to create default config.");
			e.printStackTrace();
		}
		
		System.out.println("[INFO] <CONFIG> Created default config. Exiting...");
		System.exit(0);
	}
	
	private void readConfig()
	{
		if(!new File("config.properties").exists())
			createDefaultConfig();
		
		try
		{
			ConfigReader cr = new ConfigReader(new FileInputStream("config.properties"));
			user = cr.getProperty("user");
			token = cr.getProperty("token");
			zone = cr.getProperty("zone");
			domains = cr.getProperty("domains").split(",");
			dns_zone_ids = new String[domains.length];
		}
		catch(FileNotFoundException e)
		{
			System.err.println("[ERROR] <CONFIG> Unable to find config file! This is bad since a default should have been created when there's no config.");
			e.printStackTrace();
		}
	}
}