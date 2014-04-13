/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package airnowgrib2tojson;

import java.io.BufferedOutputStream;
import org.apache.commons.net.ftp.FTPClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.apache.commons.net.ftp.FTP;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.dataset.CoordinateAxis;
import ucar.nc2.dataset.VariableDS;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridCoordSystem.*;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;

/**
 *
 * @author Thomas Leschik
 */
public class AirNowGRIB2toJSON {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        SimpleDateFormat GMT = new SimpleDateFormat("yyMMddHH");
        GMT.setTimeZone(TimeZone.getTimeZone("GMT-2"));
        
        System.out.println(GMT.format(new Date()));
        
        FTPClient ftpClient = new FTPClient();
        FileOutputStream fos = null;  
  
       try {
           //Connecting to AirNow FTP server to get the fresh AQI data  
           ftpClient.connect("ftp.airnowapi.org");  
           ftpClient.login("pixelshade", "GZDN8uqduwvk");     
           ftpClient.enterLocalPassiveMode();
           ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            
            //downloading .grib2 file
            File of = new File("US-" + GMT.format(new Date()) + "_combined.grib2");
            OutputStream outstr = 
                    new BufferedOutputStream(new FileOutputStream(of));
            InputStream instr = ftpClient.retrieveFileStream("GRIB2/US-" + 
                    GMT.format(new Date()) + "_combined.grib2");
            byte[] bytesArray = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = instr.read(bytesArray)) != -1) {
                outstr.write(bytesArray, 0, bytesRead);
            }
            
            //Close used resources
            ftpClient.completePendingCommand();
            outstr.close();
            instr.close();
            
            
            // logout the user 
            ftpClient.logout();
            
        } catch (SocketException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        } finally {  
            try {
                //disconnect from AirNow server
                ftpClient.disconnect();  
            } catch (IOException e) {  
                e.printStackTrace();  
            }  
        }  
        
        try {
            //Open .grib2 file
            final File AQIfile = new File("US-" + GMT.format(new Date()) + "_combined.grib2");
            final GridDataset gridDS = GridDataset.open(AQIfile.getAbsolutePath());
            
            //The data type needed - AQI; since it isn't defined in GRIB2 standard,
            //Aerosol type is used instead; look AirNow API documentation for details.
            GridDatatype AQI = gridDS.findGridDatatype("Aerosol_type_msl");
        
            //Get the coordinate system for selected data type;
            //cut the rectangle to work with - time and height axes aren't present in these files
            //and latitude/longitude go "-1", which means all the data provided.
            GridCoordSystem AQIGCS = AQI.getCoordinateSystem();
            List<CoordinateAxis> AQI_XY = AQIGCS.getCoordinateAxes();
            Array AQIslice = AQI.readDataSlice(0, 0, -1, -1);
            
            //Variables for iterating through coordinates
            VariableDS var = AQI.getVariable();
            Index index = AQIslice.getIndex();
                
            //Variables for counting lat/long from the indices provided
            double stepX = (AQI_XY.get(2).getMaxValue() - AQI_XY.get(2).getMinValue())/index.getShape(1);
            double stepY = (AQI_XY.get(1).getMaxValue() - AQI_XY.get(1).getMinValue())/index.getShape(0);
            double curX = AQI_XY.get(2).getMinValue();
            double curY = AQI_XY.get(1).getMinValue();
            
            //Output details
            OutputStream ValLog = new FileOutputStream("USA_AQI.json");
            Writer ValWriter = new OutputStreamWriter(ValLog);
        
            for(int j=0; j<index.getShape(0); j++){
                for(int i=0; i<index.getShape(1); i++){
                    float val = AQIslice.getFloat(index.set(j,i));
                    
                    //Write the AQI value and its coordinates if it's present by i/j indices
                    if(!Float.isNaN(val)) 
                        ValWriter.write("{\r\n\"lat\":" + curX + ",\r\n\"lng\":" + curY + ",\r\n\"AQI\":" + val + ",\r\n},\r\n");
                
                    curX+=stepX;
                }
                curY+=stepY;
                curX = AQI_XY.get(2).getMinValue();
            }       
        } catch (Exception e) {
            e.printStackTrace();
        }
    }   
}
