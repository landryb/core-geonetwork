//=============================================================================
//===	Copyright (C) 2010 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.geonet.services.publisher;


import jeeves.interfaces.Service;
import jeeves.server.ServiceConfig;
import jeeves.server.UserSession;
import jeeves.server.context.ServiceContext;
import org.fao.geonet.domain.MapServer;
import org.fao.geonet.repository.MapServerRepository;
import org.fao.geonet.utils.GeonetHttpRequestFactory;
import org.fao.geonet.utils.Log;
import org.fao.geonet.Util;
import org.fao.geonet.utils.Xml;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.kernel.setting.SettingManager;
import org.fao.geonet.lib.Lib;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to manage GeoServer dataset publication.
 * Dataset could be 
 * <ul>
 *   <li>ESRI Shapefile (zipped) POST or external</li>
 *   <li>PostGIS table</li>
 *   <li>GeoTiff (zip or not) POST or external</li>
 *   <li>ECW external</li>
 * </ul>
 * 
 * Shapefile must be zipped.
 * 
 * In case of ZIP compression, ZIP file base name must be equal to Shapefile or GeoTiff base name.
 * 
 * One Datastore, FeatureType, Layer and Style are created for a vector dataset (one to one relation).
 * One CoverageStore, Coverage, Layer are created for a raster dataset (one to one relation).
 * 
 * TODO : Support multi file publication
 * 
 */
public class Do implements Service {
	private static final String DB = "DB";

	private static final String VECTOR = "vector";

	private static final String RASTER = "raster";

	private static final String SUCCESS = "Success";

	private static final String EXCEPTION = "Exception";

	/**
	 * Module name
	 */
	public static final String MODULE = "geonetwork.GeoServerPublisher";

	/**
	 * List of current known nodes
	 */
	private HashMap<Integer, GeoServerNode> geoserverNodes = new HashMap<Integer, GeoServerNode>();

	/**
	 * List of current known restEndpoint
	 */
	private HashMap<String, GeoServerRest> geoserverRestList = new HashMap<String, GeoServerRest>();

	/**
	 * List of current known workspaces
	 */
	private HashMap<String, String> geoserverWorkspaceList = new HashMap<String, String>();

	/**
	 * Error code received when publishing
	 */
	private String errorCode = "";

	/**
	 * Report return by a read action
	 */
	private Element report = null;

	private Element getReport() {
		return report;
	}

	private void setReport(Element report) {
		this.report = report;
	}

	private String getErrorCode() {
		return errorCode;
	}

	private void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	/**
	 * Load configuration file and register remote nodes. In order to register
	 * new nodes, restart is needed.
	 * 
	 */
	public void init(Path appPath, ServiceConfig params) throws Exception {
		Log.createLogger(MODULE);
	}

    /**
     * Publish a dataset to a remote GeoServer node. Dataset could be a ZIP
     * composed of Shapefile(s) or GeoTiff.
     * 
     * updataMetadataRecord, add or delete a online source link.
     */
    public Element exec(Element params, ServiceContext context)
    		throws Exception {
        MapServerRepository repo = context.getBean(MapServerRepository.class);
    	
    	ACTION action = ACTION.valueOf(Util.getParam(params, "action"));
    	if (action.equals(ACTION.LIST)) {
            return loadDbConfiguration(context);
    	} else if (action.equals(ACTION.ADD_NODE)) {
            MapServer m = new MapServer()
                    .setName(Util.getParam(params, "name", ""))
                    .setDescription(Util.getParam(params, "description", ""))
                    .setConfigurl(Util.getParam(params, "configurl", ""))
                    .setWmsurl(Util.getParam(params, "wmsurl", ""))
                    .setWfsurl(Util.getParam(params, "wfsurl", ""))
                    .setWcsurl(Util.getParam(params, "wcsurl", ""))
                    .setStylerurl(Util.getParam(params, "stylerurl", ""))
                    .setUsername(Util.getParam(params, "username", ""))
                    .setPassword(Util.getParam(params, "password", ""))
                    .setNamespace(Util.getParam(params, "namespace", ""))
                    .setNamespacePrefix(Util.getParam(params, "namespaceprefix", ""));
            context.getBean(MapServerRepository.class).save(m);
            return new Element(action.toString())
                        .setText("ok")
                        .setAttribute("id", String.valueOf(m.getId()));
        } else if (action.equals(ACTION.REMOVE_NODE)) {
            MapServer m = repo.findOneById(Util.getParam(params, "id"));
            if (m != null) {
                repo.delete(m);
            }
            return new Element(action.toString()).setText("ok");
        } else if (action.equals(ACTION.UPDATE_NODE)) {
            MapServer m = repo.findOneById(Util.getParam(params, "id"));
            if (m != null) {
                m.setName(Util.getParam(params, "name", ""))
                    .setDescription(Util.getParam(params, "description", ""))
                    .setConfigurl(Util.getParam(params, "configurl", ""))
                    .setWmsurl(Util.getParam(params, "wmsurl", ""))
                    .setWfsurl(Util.getParam(params, "wfsurl", ""))
                    .setWcsurl(Util.getParam(params, "wcsurl", ""))
                    .setStylerurl(Util.getParam(params, "stylerurl", ""))
                    .setNamespace(Util.getParam(params, "namespace", ""))
                    .setNamespacePrefix(Util.getParam(params, "namespaceprefix", ""));
                repo.save(m);
            }
            return new Element(action.toString()).setText("ok");
        } else if (action.equals(ACTION.UPDATE_NODE_ACCOUNT)) {
            MapServer m = repo.findOneById(Util.getParam(params, "id"));
            if (m != null) {
                m.setUsername(Util.getParam(params, "username", ""))
                    .setPassword(Util.getParam(params, "password", ""));
                repo.save(m);
            }
            return new Element(action.toString()).setText("ok");
        } else if (action.equals(ACTION.CREATE) || action.equals(ACTION.UPDATE)
    			|| action.equals(ACTION.DELETE) || action.equals(ACTION.GET)) {
    
    		// Check parameters
		String nodeId = Util.getParam(params, "nodeId");
    		String metadataId = Util.getParam(params, "metadataId");
    		String metadataUuid = Util.getParam(params, "metadataUuid", "");
    		// purge \\n from metadataTitle - geoserver prefers layer titles on a single line
    		String metadataTitle = Util.getParam(params, "metadataTitle", "").replace("\\n","");
    		// unescape \\n from metadataAbstract so they're properly sent to geoserver
    		String metadataAbstract = Util.getParam(params, "metadataAbstract", "").replace("\\n","\n");

		GeoServerRest restEndpoint = geoserverRestList.get(nodeId);
		String ws = geoserverWorkspaceList.get(nodeId);
    
    		String file = Util.getParam(params, "file");
    		String access = Util.getParam(params, "access");
    
    		//jdbc:postgresql://host:port/user:password@database#table
    		if (file.startsWith("jdbc:postgresql")) {
    			String[] values = file.split("/");
    			
    			String[] serverInfo = values[2].split(":");
    			String host = serverInfo[0];
    			String port = serverInfo[1];
    			
    			String[] dbUserInfo = values[3].split("@");
    			
    			String[] userInfo = dbUserInfo[0].split(":");
    			String user = userInfo[0];
    			String password = userInfo[1];
    			
    			String[] dbInfo = dbUserInfo[1].split("#");
    			String db = dbInfo[0]; 
    			String table = dbInfo[1]; 
    			
    			return publishDbTable(action, ws, restEndpoint, "postgis", host, port, user, password, db, table, "postgis", metadataUuid, metadataTitle, metadataAbstract);
    		} else {
    		    if (file.startsWith("file://") || file.startsWith("http://")) {
    		        return addExternalFile(action, ws, restEndpoint, file, metadataUuid, metadataTitle, metadataAbstract);
    		    } else {
    		        // Get ZIP file from data directory
		        Path f = Lib.resource.getDir(context, access, metadataId).resolve(file);
		        return addZipFile(action, ws, restEndpoint, f, file, metadataUuid, metadataTitle, metadataAbstract);
    		    }
    		}
    	}
    	return null;
    }

    private Element loadDbConfiguration(ServiceContext context) {
        final GeonetHttpRequestFactory requestFactory = context.getBean(GeonetHttpRequestFactory.class);
    	GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);
        SettingManager settingsManager = gc.getBean(SettingManager.class);
    	String baseUrl = settingsManager.getValue(Geonet.Settings.SERVER_PROTOCOL)
    			+ "://" + settingsManager.getValue(Geonet.Settings.SERVER_HOST)
    			+ ":" + settingsManager.getValue("system/server/port")
    			+ context.getBaseUrl();

	UserSession session = context.getUserSession();
	Collection<String> geopub = session.getPrincipal().getGeopublicationPrivileges();

        final java.util.List<MapServer> mapservers =
                context.getBean(MapServerRepository.class)
                        .findAll();
        geoserverNodes.clear();
        Element geoserverConfig = new Element("nodes");
        for (MapServer m : mapservers) {
            GeoServerRest gsr = new GeoServerRest(requestFactory, m.getConfigurl(),
                    m.getUsername(), m.getPassword(), m.getDatadirPath(),
                    m.getNamespacePrefix(), baseUrl);
		GeoServerNode g = new GeoServerNode(m);
		g.setRest(gsr);
		try {
			// if the remote server is not discoverable, default to the workspace given in the config
			Collection<String> wsnames = new LinkedList<String>();
			wsnames.add(m.getNamespacePrefix());
			if (m.shouldDiscoverWorkspaces()) {
				wsnames = gsr.listAvailableWorkspaces();
				Log.debug(MODULE, "Updating list of available workspaces for node id " + m.getId()  + " on REST endpoint " + m.getConfigurl());

				/* create user workspaces if missing */
				if (!geopub.isEmpty()) {
					String dbg = "Logged in user can write to workspace: ";
					for (String s : geopub) {
						dbg += " " + s;
						if (!wsnames.contains(s)) {
							/* XXX try to create in the geoserver only if it's "dynamic" */
							Log.debug(MODULE, "Workspace " + s + " not existing, creating it");
							g.getRest().createWorkspace(s);
							wsnames.add(s);
						}
					}
					Log.debug(MODULE, dbg);
				}
			}
			for (String ws : wsnames) {
				Element node = new Element("node");
				node.addContent(new Element("id").setText(g.getId() + "-" + ws));
				node.addContent(new Element("name").setText(g.getName() + "-" + ws));
				node.addContent(new Element("description").setText(m.getDescription()));
				node.addContent(new Element("namespacePrefix").setText(ws));
				node.addContent(new Element("namespaceUrl").setText(g.getNamespaceUrl() + "/" + ws));
				node.addContent(new Element("adminUrl").setText(m.getConfigurl()));
				node.addContent(new Element("wmsUrl").setText(g.getPublicUrl() + ws + "/wms"));
				node.addContent(new Element("wfsUrl").setText(g.getPublicUrl() + ws + "/wfs"));
				node.addContent(new Element("wcsUrl").setText(g.getPublicUrl() + ws + "/wcs"));
				node.addContent(new Element("stylerUrl").setText(g.getPublicUrl() + "/www/styler/index.html")); //XXX
				/* XXX show all workspaces if geopub is empty ? */
				if (!m.shouldDiscoverWorkspaces() || geopub.isEmpty() || geopub.contains(ws))
					geoserverConfig.addContent(node);
				geoserverNodes.put(m.getId(), g);
				geoserverRestList.put(g.getId() + "-" + ws, g.getRest());
				geoserverWorkspaceList.put(g.getId() + "-" + ws, ws);
			}
		} catch (Exception e) {
			Log.error(MODULE, "Failed to get available (or create) workspaces in node " + m.getId() + ", Exception " + e.getMessage());
		}
        }
        return geoserverConfig;
    }

	/**
	 * List of action valid for publisher service
	 */
	private enum ACTION {
		/**
		 * Return list of nodes
		 */
		LIST, CREATE, UPDATE, DELETE, GET,
        ADD_NODE, REMOVE_NODE, UPDATE_NODE,
        UPDATE_NODE_ACCOUNT
    };

	/**
	 * Register a database table in GeoServer
	 * 
	 * @param action
	 * @param g
	 * @param string
	 * @param host
	 * @param port
	 * @param user
	 * @param password
	 * @param db
	 * @param table
	 * @param dbType
	 * @param metadataUuid TODO
	 * @param metadataTitle TODO
	 * @return
	 */
	private Element publishDbTable(ACTION action, String ws, GeoServerRest g, String string,
			String host, String port, String user, String password, String db,
			String table, String dbType, String metadataUuid, String metadataTitle, String metadataAbstract) {
		try {
			if (action.equals(ACTION.CREATE) || action.equals(ACTION.UPDATE)) {
				StringBuilder report = new StringBuilder();
				// TODO : check datastore already exist
				if (!g.createDatabaseDatastore(ws, db, host, port, db, user, password, dbType))
					report.append("Datastore: ").append(g.getStatus());
				if (!g.createFeatureType(ws, db, table, true, metadataUuid, metadataTitle, metadataAbstract))
					report.append("Feature type: ").append(g.getStatus());
//				Publication of Datastore and feature type may failed if already exist
//				if (report.length() > 0) {
//					setErrorCode(report.toString());
//					return report(EXCEPTION, DB, getErrorCode());
//				}
			} else if (action.equals(ACTION.DELETE)) {
			    StringBuilder report = new StringBuilder();
				if (!g.deleteLayer(table))
					report.append("Layer: ").append(g.getStatus());
//				Only remove the layer in such situation
//				if (!g.deleteFeatureType(ws, db, table))
//					report.append("Feature type: ").append(g.getStatus());
//				if (!g.deleteDatastore(ws, db))
//					report.append("Datastore: ").append(g.getStatus());

				if (report.length() > 0) {
					setErrorCode(report.toString());
					return report(EXCEPTION, DB, getErrorCode());
				}
			}
			
			if (g.getLayer(table)) {
				setReport(Xml.loadString(g.getResponse(), false));
				return report(SUCCESS, DB, getReport());
			} else {
				setErrorCode(g.getStatus() + "");
				return report(EXCEPTION, DB, getErrorCode());
			}

		} catch (Exception e) {
			setErrorCode(e.getMessage());
			Log.error(MODULE, "Exception " + e.getMessage());
		}		return report(EXCEPTION, DB, getErrorCode());
	}

	/**
	 * Analyze ZIP file content and if valid, push the data
	 * to GeoServer.
	 * 
	 * @param action
	 * @param gs
	 * @param f
     *@param file  @return
	 * @throws java.io.IOException
	 */
	private Element addZipFile(ACTION action, String ws, GeoServerRest gs, Path f, String file, String metadataUuid, String metadataTitle, String metadataAbstract)
			throws IOException {
		if (f == null) {
			return report(EXCEPTION, null,
					"Could not find dataset file. Invalid zip file parameters: "
							+ file + ".");
		}

		// Handle multiple geofile.
		GeoFile gf = new GeoFile(f);

		Collection<String> rasterLayers, vectorLayers;

		try {
			vectorLayers = gf.getVectorLayers(true);
			if (vectorLayers.size() > 0) {
				if (publishVector(f, ws, gs, action, metadataUuid, metadataTitle, metadataAbstract)) {
					return report(SUCCESS, VECTOR, getReport());
				} else {
					return report(EXCEPTION, VECTOR, getErrorCode());
				}
			}
		} catch (IllegalArgumentException e) {
			return report(EXCEPTION, VECTOR, e.getMessage());
		}

		try {
			rasterLayers = gf.getRasterLayers();
			if (rasterLayers.size() > 0) {
				if (publishRaster(f, ws, gs, action, metadataUuid, metadataTitle, metadataAbstract)) {
					return report(SUCCESS, RASTER, getReport());
				} else {
					return report(EXCEPTION, RASTER, getErrorCode());
				}
			}
		} catch (IllegalArgumentException e) {
			return report(EXCEPTION, RASTER, e.getMessage());
		}

		if (vectorLayers.size() == 0 && rasterLayers.size() == 0) {
			return report(EXCEPTION, RASTER,
					"No vector or raster layers found in file (" + file
							+ ").");
		}
		return null;
	}

	private Element addExternalFile(ACTION action, String ws, GeoServerRest gs, String file, String metadataUuid, String metadataTitle, String metadataAbstract)
            throws IOException {
	    // TODO vector or raster file ? Currently GeoServer does not support RASTER for external
	    if (publishExternal(file, ws, gs, action, metadataUuid, metadataTitle, metadataAbstract)) {
            return report(SUCCESS, VECTOR, getReport());
        } else {
            return report(EXCEPTION, VECTOR, getErrorCode());
        }
	}
	
	private Element report(String name, String type, String msg) {
		Element report = new Element(name);
		if (type != null)
			report.setAttribute("type", type);
		report.setAttribute("status", msg);
		return report;
	}

	private Element report(String name, String type, Element msg) {
		Element report = new Element(name);
		if (type != null)
			report.setAttribute("type", type);
		report.addContent(msg);
		return report;
	}

	private boolean publishVector(Path f, String ws, GeoServerRest g, ACTION action, String metadataUuid, String metadataTitle, String metadataAbstract) {

		String ds = f.getFileName().toString();
		String dsName = ds.substring(0, ds.lastIndexOf("."));
		try {
			if (action.equals(ACTION.CREATE)) {
				g.createDatastore(ws, dsName, f, true);
				g.createFeatureType(ws, dsName, dsName, false, metadataUuid, metadataTitle, metadataAbstract);
			} else if (action.equals(ACTION.UPDATE)) {
				g.createDatastore(ws, dsName, f, false);
				g.createFeatureType(ws, dsName, dsName, false, metadataUuid, metadataTitle, metadataAbstract);
			} else if (action.equals(ACTION.DELETE)) {
				String report = "";
				if (!g.deleteLayer(dsName))
					report += "Layer: " + g.getStatus();
				if (!g.deleteFeatureType(ws, dsName, dsName))
					report += "Feature type: " + g.getStatus();
				if (!g.deleteDatastore(ws, dsName))
					report += "Datastore: " + g.getStatus();

				if (!report.equals("")) {
					setErrorCode(report);
					return false;
				}
			}
			if (g.getLayer(dsName)) {
				setReport(Xml.loadString(g.getResponse(), false));
			} else {
				setErrorCode(g.getStatus() + "");
				return false;
			}
			return true;

		} catch (Exception e) {
			setErrorCode(e.getMessage());
			Log.error(MODULE, "Exception " + e.getMessage());
		}
		return false;
	}

	private boolean publishExternal(String file, String ws, GeoServerRest g, ACTION action, String metadataUuid, String metadataTitle, String metadataAbstract) {

        String dsName = file.substring(file.lastIndexOf("/") + 1, file.lastIndexOf("."));
        boolean isRaster = GeoFile.fileIsRASTER(file);
        Log.error(MODULE, "Publish external: " + dsName + ", Raster: " + isRaster);
        try {
            if (action.equals(ACTION.CREATE)) {
                if (isRaster) {
                	g.createCoverage(ws, dsName, file, metadataUuid, metadataTitle, metadataAbstract);
                } else {
                    g.createDatastore(dsName, file, true);
                }
            } else if (action.equals(ACTION.UPDATE)) {
                if (isRaster) {
                	g.createCoverage(ws, dsName, file, metadataUuid, metadataTitle, metadataAbstract);
                } else {
                    g.createDatastore(ws, dsName, file, false);
                }
            } else if (action.equals(ACTION.DELETE)) {
                String report = "";
                if (!g.deleteLayer(dsName))
                    report += "Layer: " + g.getStatus();
                if (isRaster) {
                    
                } else {
                    if (!g.deleteFeatureType(ws, dsName, dsName))
                        report += "Feature type: " + g.getStatus();
                    if (!g.deleteDatastore(ws, dsName))
                        report += "Datastore: " + g.getStatus();
                }
                if (!report.equals("")) {
                    setErrorCode(report);
                    return false;
                }
            }
            if (g.getLayer(dsName)) {
                setReport(Xml.loadString(g.getResponse(), false));
            } else {
                setErrorCode(g.getStatus() + "");
                return false;
            }
            return true;

        } catch (Exception e) {
            setErrorCode(e.getMessage());
            Log.error(MODULE, "Exception " + e.getMessage());
        }
        return false;
    }
	private boolean publishRaster(Path f, String ws, GeoServerRest g, ACTION action, String metadataUuid, String metadataTitle, String metadataAbstract) {
		String cs = f.getFileName().toString();
		String csName = cs.substring(0, cs.lastIndexOf("."));
		try {
			if (action.equals(ACTION.CREATE)) {
				g.createCoverage(ws, csName, f, metadataUuid, metadataTitle, metadataAbstract);
			} else if (action.equals(ACTION.UPDATE)) {
				g.createCoverage(ws, csName, f, metadataUuid, metadataTitle, metadataAbstract);
			} else if (action.equals(ACTION.DELETE)) {
				String report = "";
				if (!g.deleteLayer(csName))
					report += "Layer: " + g.getStatus();
				if (!g.deleteCoverage(ws, csName, csName))
					report += "Coverage: " + g.getStatus();
				if (!g.deleteCoverageStore(ws, csName))
					report += "Coveragestore: " + g.getStatus();

				if (!report.equals("")) {
					setErrorCode(report);
					return false;
				}
			}
			if (g.getLayer(csName)) {
				setReport(Xml.loadString(g.getResponse(), false));
			} else {
				setErrorCode(g.getStatus() + "");
				return false;
			}
			return true;

		} catch (Exception e) {
			setErrorCode(e.getMessage());
			Log.error(MODULE, "Exception " + e.getMessage());
		}
		return false;
	}
}
