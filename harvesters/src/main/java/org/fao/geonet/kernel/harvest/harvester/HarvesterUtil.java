package org.fao.geonet.kernel.harvest.harvester;

import org.fao.geonet.Logger;
import org.fao.geonet.domain.Pair;
import org.fao.geonet.kernel.schema.MetadataSchema;
import org.fao.geonet.utils.Xml;
import org.jdom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by francois on 3/7/14.
 */
public class HarvesterUtil {
    public static Pair<String, Map<String, Object>> parseXSLFilter(String filter,
                                Logger log) {
        String processName = filter;
        Map<String, Object> processParams = new HashMap<String, Object>();

        // Parse complex xslfilter process_name?process_param1=value&process_param2=value...
        if (filter.contains("?")) {
            String[] filterInfo = filter.split("\\?");
            processName = filterInfo[0];
            if(log.isDebugEnabled()) log.debug("      - XSL Filter name:" + processName);
            if (filterInfo[1] != null) {
                String[] filterKVP = filterInfo[1].split("&");
                for (String kvp : filterKVP) {
                    String[] param = kvp.split("=");
                    if (param.length == 2) {
                        if(log.isDebugEnabled()) {
                            log.debug("        with param:" + param[0] + " = " + param[1]);
                        }
                        processParams.put(param[0], param[1]);
                    } else {
                        if(log.isDebugEnabled()) {
                            log.debug("        no value for param: "
                                + param[0]);
                        }
                    }
                }
            }
        }
        return Pair.read(processName, processParams);
    }


    /**
     * Filter the metadata if process parameter is set and
     * corresponding XSL transformation exists.
     *
     * @param metadataSchema
     * @param md
     *
     * @param processParams
     * @return
     */
    public static Element processMetadata(MetadataSchema metadataSchema,
                                          Element md,
                                          String processName,
                                          Map<String, Object> processParams,
                                          Logger log) {

        Path filePath = metadataSchema.getSchemaDir().resolve("process").resolve(processName + ".xsl");
        if (!Files.exists(filePath)) {
            log.info("     processing instruction not found for " + metadataSchema.getName() + " schema. metadata not filtered.");
        } else {
            Element processedMetadata;
            try {
                processedMetadata = Xml.transform(md, filePath, processParams);
                if(log.isDebugEnabled()) log.debug("     metadata filtered.");
                md = processedMetadata;
            } catch (Exception e) {
                log.warning("     processing error (" + processName + "): "
                        + e.getMessage());
            }
        }
        return md;
    }
}
