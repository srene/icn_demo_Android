

package uk.ac.ucl.ndnocr.utils;

public class Config
{
	// All times are in milliseconds.

	public static final String prefix ="/exec/OCR/";
	public static final String video ="video";
	public static final String text ="Emergency video for the ndnocr demo";


	//How long to wait for a connection to be successful
	public static final long wifiConnectionWaitingTime = 30000;

	public static final long wifiScanTime = 10000;


	public static final long createFaceWaitingTime = 2000;

	public static final long interestLifeTime = 60000;


	//Tries to create a face if failed because network not ready
	public static final int maxRetry = 5;

	public static final int nfdMaxRetry = 10;

	public static final String passwd = "Raspberry";

	public static final String SSID = "NDNOCR";

	public static final String GW_IP ="192.168.49.1";


} // Config
