/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.updater.accessible;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import com.fasterxml.jackson.databind.JsonNode;

import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraversalRequirements;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.updater.GraphUpdaterManager;
import org.opentripplanner.updater.GraphWriterRunnable;
import org.opentripplanner.updater.PollingGraphUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opentripplanner.routing.impl.CandidateEdgeBundle;
import org.opentripplanner.routing.impl.StreetVertexIndexServiceImpl;

/**
 * This class shows an example of how to implement a polling graph updater. Besides implementing the
 * methods of the interface PollingGraphUpdater, the updater also needs to be registered in the
 * function GraphUpdaterConfigurator.applyConfigurationToGraph.
 *
 * This example is suited for polling updaters. For streaming updaters (aka push updaters) it is
 * better to use the GraphUpdater interface directly for this purpose. The class ExampleGraphUpdater
 * shows an example of how to implement this.
 *
 * Usage example ('polling-example' name is an example) in file 'Graph.properties':
 *
 * <pre>
 * polling-example.type = example-polling-updater
 * polling-example.frequencySec = 60
 * polling-example.url = https://api.updater.com/example-polling-updater
 * </pre>
 *
 * @see ExampleGraphUpdater
 * @see GraphUpdaterConfigurator.applyConfigurationToGraph
 */
public class AccessibleGraphUpdater extends PollingGraphUpdater {

    private static Logger LOG = LoggerFactory.getLogger(AccessibleGraphUpdater.class);

    private GraphUpdaterManager updaterManager;

    private String url;

    private AccessibleDataSource source;

    // Here the updater can be configured using the properties in the file 'Graph.properties'.
    // The property frequencySec is already read and used by the abstract base class.
    @Override
    protected void configurePolling(Graph graph, JsonNode config) throws Exception {
        url = config.path("url").asText(); // Can also set this manually for testing
        LOG.info("Configured example polling updater: frequencySec={} and url={}", frequencySec, url);
        this.source = new AccessibleDataSource();
        // Need to configure to actually pass values to the object
        this.source.configure(graph, config);
    }

    // Here the updater gets to know its parent manager to execute GraphWriterRunnables.
    @Override
    public void setGraphUpdaterManager(GraphUpdaterManager updaterManager) {
        LOG.info("Example polling updater: updater manager is set");
        this.updaterManager = updaterManager;
    }

    // Here the updater can be initialized.
    @Override
    public void setup() {
        LOG.info("Setup example polling updater");
    }

    // This is where the updater thread receives updates and applies them to the graph.
    // This method will be called every frequencySec seconds.
    @Override
    protected void runPolling() throws Exception {
        LOG.info("Run example polling updater with hashcode: {}", this.hashCode());

        // Fetch initial data, and check if was successful
        if (source.update()) {
        	// Returns locations to be processed
        	List<GenericLocation> locations = source.getLocations();
            LOG.info("Fetched list of " + locations);
            AccessibleGraphWriter graphWriter = new AccessibleGraphWriter(locations);

            // Execute example graph writer
            updaterManager.execute(graphWriter);
        }
        else {
        	LOG.info("Error returned fetching data");
        }

    }

    // Here the updater can cleanup after itself.
    @Override
    public void teardown() {
        LOG.info("Teardown example polling updater");
    }

    // This is a private GraphWriterRunnable that can be executed to modify the graph
    private class AccessibleGraphWriter implements GraphWriterRunnable {

    	private List<GenericLocation> locations;

    	public AccessibleGraphWriter(List<GenericLocation> locations) {
            this.locations = locations;
        }

        @Override
        public void run(Graph graph) {
            LOG.info("AccessibleGraphWriter {} runnable is run on the "
                            + "graph writer scheduler.", this.hashCode());

            // Create service for finding nearest street edge to location
            StreetVertexIndexServiceImpl accessibleService = new StreetVertexIndexServiceImpl(graph);

            // Make RoutingRequest and Traversal for running the index service
            RoutingRequest req = new RoutingRequest();
            req.setModes(new TraverseModeSet("WALK"));

            // Can't set req accessible, otherwise won't be able to turn back on edges that are not accessible
            TraversalRequirements traversalOptions = new TraversalRequirements(req);

            for (GenericLocation location : locations) {
            	// Check if returns any edges, if so get best edge
            	CandidateEdgeBundle edgeResults = accessibleService.getClosestEdges(location, traversalOptions);
            	if (edgeResults.size() > 0) {
            		StreetEdge edgeToModify = accessibleService.getClosestEdges(location, traversalOptions).best.edge;
            		// Check if location is active, change accessibility accordingly
                	if (location.name.equals("open")) {
                        edgeToModify.setWheelchairAccessible(false);
                        LOG.info("Set as NOT wheelchair accessible " + edgeToModify.toString());
                	}
                	else {
                        edgeToModify.setWheelchairAccessible(true);
                        LOG.info("Set as wheelchair accessible " + edgeToModify.toString());
                	}
            	}

            }

            // PRODUCTION LOCATION
            // File graphFile = new File("/var/otp/graphs/chicago/Graph.obj");
            File graphFile = new File("/Users/pjsier/Code/OpenTripPlanner/graphs/chicago/Graph.obj");
            try {
				graph.save(graphFile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        }
    }
}
