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
           // pass directory path on server to connect  
           ftpClient.connect("ftp.airnowapi.org");  
           ftpClient.login("pixelshade", "GZDN8uqduwvk");
                
           ftpClient.enterLocalPassiveMode();
           ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            
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
 
            boolean success = ftpClient.completePendingCommand();
            
            outstr.close();
            instr.close();
            
            if (success) {
                System.out.println("File #2 has been downloaded successfully.");
            }
            
            // logout the user, returned true if logout successfully  
            ftpClient.logout();
            
        } catch (SocketException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        } finally {  
            try {  
                ftpClient.disconnect();  
            } catch (IOException e) {  
                e.printStackTrace();  
            }  
        }  
        
        try {
            //Example GRIB2 file; later implement automatic load of 
            //the last available grib2 file from AirNow FTP
            final File AQIfile = new File("US-" + GMT.format(new Date()) + "_combined.grib2");
            final GridDataset gridDS = GridDataset.open(AQIfile.getAbsolutePath());
            GridDatatype AQI = gridDS.findGridDatatype("Aerosol_type_msl");
        
            GridCoordSystem AQIGCS = AQI.getCoordinateSystem();
        
            List<CoordinateAxis> AQI_XY = AQIGCS.getCoordinateAxes();
        
            Array AQIslice = AQI.readDataSlice(0, 0, -1, -1);
        
            VariableDS var = AQI.getVariable();
        
            Index index = AQIslice.getIndex();
                
            double stepX = (AQI_XY.get(2).getMaxValue() - AQI_XY.get(2).getMinValue())/index.getShape(1);
            double stepY = (AQI_XY.get(1).getMaxValue() - AQI_XY.get(1).getMinValue())/index.getShape(0);
        
            double curX = AQI_XY.get(2).getMinValue();
            double curY = AQI_XY.get(1).getMinValue();
        
            OutputStream ValLog = new FileOutputStream("USA_AQI.txt");
            Writer ValWriter = new OutputStreamWriter(ValLog);
        
            for(int j=0; j<index.getShape(0); j++){
                for(int i=0; i<index.getShape(1); i++){
                    float val = AQIslice.getFloat(index.set(j,i));
                
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
