package org.opentripplanner.updater.accessible;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import org.opentripplanner.updater.JsonConfigurable;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Fetch Accessibility data JSON feeds and pass each record on to the specific rental subclass
 *
 * @see BikeRentalDataSource
 */
public class AccessibleDataSource implements JsonConfigurable {

    private static final Logger log = LoggerFactory.getLogger(AccessibleDataSource.class);
    private String url;
    private String apiKey;

    private String jsonParsePath;

    ArrayList<GenericLocation> locations = new ArrayList<GenericLocation>();

    /**
     * Construct superclass
     *
     * @param JSON path to get from enclosing elements to nested rental list.
     *        Separate path levels with '/' For example "d/list"
     *
     */
    public AccessibleDataSource(String jsonPath) {
        jsonParsePath = jsonPath;
        apiKey= null;
    }

    /**
     * Construct superclass
     *
     * @param JSON path to get from enclosing elements to nested rental list.
     *        Separate path levels with '/' For example "d/list"
     * @param Api key, when used by bike rental type
     *
     */
    public AccessibleDataSource(String jsonPath, String apiKeyValue) {
        jsonParsePath = jsonPath;
        apiKey = apiKeyValue;
    }


    /**
     * Construct superclass where rental list is on the top level of JSON code
     *
     */
    public AccessibleDataSource() {
        jsonParsePath = "";
    }

    public boolean update() {
        try {
            InputStream data = HttpUtils.getData(url);
            if (data == null) {
                log.warn("Failed to get data from url " + url);
                return false;
            }
            parseJSON(data);
            data.close();
        } catch (IllegalArgumentException e) {
            log.warn("Error parsing accessibility data feed from " + url, e);
            return false;
        } catch (JsonProcessingException e) {
            log.warn("Error parsing accessibility data feed from " + url + "(bad JSON of some sort)", e);
            return false;
        } catch (IOException e) {
            log.warn("Error reading accessibility data feed from " + url, e);
            return false;
        }
        return true;
    }

    private void parseJSON(InputStream dataStream) throws JsonProcessingException, IllegalArgumentException,
      IOException {

    	ArrayList<GenericLocation> out = new ArrayList<GenericLocation>();

        String locationString = convertStreamToString(dataStream);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(locationString);

        if (!jsonParsePath.equals("")) {
            String delimiter = "/";
            String[] parseElement = jsonParsePath.split(delimiter);
            for(int i =0; i < parseElement.length ; i++) {
                rootNode = rootNode.path(parseElement[i]);
            }

            if (rootNode.isMissingNode()) {
                throw new IllegalArgumentException("Could not find jSON elements " + jsonParsePath);
              }
        }

        for (int i = 0; i < rootNode.size(); i++) {
            JsonNode node = rootNode.get(i);
            if (node == null) {
                continue;
            }
            // Parse for active as way of determining whether to turn on or off wheelchair accessibility
            String active = node.path("status").asText();
            String coordinates = node.path("coords").asText();
            GenericLocation locationNode = new GenericLocation(active, coordinates);
            if (locationNode != null)
                out.add(locationNode);
        }
        synchronized(this) {
            locations = out;
        }
    }

    private String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner scanner = null;
        String result="";
        try {
           
            scanner = new java.util.Scanner(is).useDelimiter("\\A");
            result = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
        }
        finally
        {
           if(scanner!=null)
               scanner.close();
        }
        return result;
        
    }

    //@Override
    public synchronized List<GenericLocation> getLocations() {
        return locations;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return getClass().getName() + "(" + url + ")";
    }

    /**
     * Note that the JSON being passed in here is for configuration of the OTP component, it's completely separate
     * from the JSON coming in from the update source.
     */
    @Override
    public void configure (Graph graph, JsonNode jsonNode) {
        String url = jsonNode.path("url").asText(); // path() returns MissingNode not null.
        if (url == null) {
            throw new IllegalArgumentException("Missing mandatory 'url' configuration.");
        }
        this.url = url;
    }
}
