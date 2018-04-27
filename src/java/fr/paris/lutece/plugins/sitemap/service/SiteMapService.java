/*
 * Copyright (c) 2002-2017, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.sitemap.service;

import fr.paris.lutece.portal.business.page.Page;
import fr.paris.lutece.portal.business.page.PageHome;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.sql.DAOUtil;
import fr.paris.lutece.util.xml.XmlUtil;


/**
 * Site Map Service
 */
public final class SiteMapService
{
    private static final String TAG_URL = "url";
    private static final String TAG_LOC = "loc";
    private static final String TAG_PRIORITY = "priority";
    private static final String TAG_LAST_MOD = "lastmod";
    private static final String TAG_CHANGE_FREQ = "changefreq";
    private static final String DEFAULT_DATE = "2007-12-23";
    private static final String PROPERTY_LUTECE_PROD_URL = "lutece.prod.url";
    private static final String PROPERTY_CHANGE_FREQUENCY_PAGE = "sitemap.page.changefreq";
    private static final String PROPERTY_CHANGE_FREQUENCY_DOCUMENT = "sitemap.document.changefreq";
    private static final String SQL_FIND_DOCUMENTS = "SELECT document_published.id_document, core_portlet.id_portlet , core_portlet.date_update " +
        " FROM core_portlet, document_published " +
        " WHERE core_portlet.id_portlet_type = 'DOCUMENT_LIST_PORTLET' and core_portlet.id_portlet = document_published.id_portlet and core_portlet.id_page = ?";
    private static final String SQL_FIND_PAGES = "select date_update from core_page where core_page.id_page = ?";

    /**
     * Private constructor
     */
    private SiteMapService(  )
    {
    }

    /**
     * Build the sitemap XML file
     * @return The sitemap as a String
     */
    public static String getXmlSiteMap(  )
    {
        StringBuffer strXml = new StringBuffer(  );
        strXml.append( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" );
        strXml.append( 
            "<urlset xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n\t xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9\n\t http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\"\n\t xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n" );
        findPages( strXml, 1, 0 );
        strXml.append( "</urlset>" );

        return strXml.toString(  );
    }

    /**
     * Recursive crawling of all pages
     * @param strXml The XML document to fill
     * @param nPageId The current page id
     * @param nLevel The current level
     */
    private static void findPages( StringBuffer strXml, int nPageId, int nLevel )
    {
        Page page = PageHome.getPage( nPageId );
        Double priority = new Double( 1 ) / ( nLevel + 1 );
        String strChangeFrequency = AppPropertiesService.getProperty( PROPERTY_CHANGE_FREQUENCY_PAGE );
        String strBaseUrl = AppPropertiesService.getProperty( PROPERTY_LUTECE_PROD_URL );

        if ( page.isVisible( null ) )
        {
            XmlUtil.beginElement( strXml, TAG_URL );
            XmlUtil.addElement( strXml, TAG_LOC, strBaseUrl + "/jsp/site/Portal.jsp?page_id=" + page.getId(  ) );
            XmlUtil.addElement( strXml, TAG_PRIORITY, priority.toString(  ) );
            XmlUtil.addElement( strXml, TAG_LAST_MOD, pageModificationDate( nPageId ) );
            XmlUtil.addElement( strXml, TAG_CHANGE_FREQ, strChangeFrequency );
            XmlUtil.endElement( strXml, TAG_URL );

            findDocuments( strXml, page.getId(  ), priority );

            for ( Page pageChild : PageHome.getChildPages( nPageId ) )
            {
                findPages( strXml, pageChild.getId(  ), nLevel + 1 );
            }
        }
    }

    /**
     * Gets a the last modification date of a given page
     * @param nPageId The page ID
     * @return The date as a String
     */
    private static String pageModificationDate( int nPageId )
    {
        String strModificationDate = DEFAULT_DATE;
        DAOUtil daoUtil = new DAOUtil( SQL_FIND_PAGES );
        daoUtil.setInt( 1, nPageId );
        daoUtil.executeQuery(  );

        if ( daoUtil.next(  ) )
        {
            strModificationDate = daoUtil.getString( 1 ).substring( 0, 10 );
        }

        daoUtil.free(  );

        return strModificationDate;
    }

    /**
     *
     * @param strXml The XML document to String
     * @param nPageId The current page ID
     * @param priority The priority
     */
    private static void findDocuments( StringBuffer strXml, int nPageId, Double priority )
    {
        String strChangeFrequency = AppPropertiesService.getProperty( PROPERTY_CHANGE_FREQUENCY_DOCUMENT );
        String strBaseUrl = AppPropertiesService.getProperty( PROPERTY_LUTECE_PROD_URL );
        DAOUtil daoUtil = new DAOUtil( SQL_FIND_DOCUMENTS );
        daoUtil.setInt( 1, nPageId );
        daoUtil.executeQuery(  );

        while ( daoUtil.next(  ) )
        {
            XmlUtil.beginElement( strXml, TAG_URL );
            XmlUtil.addElement( strXml, TAG_LOC,
                strBaseUrl + "/jsp/site/Portal.jsp?document_id=" + daoUtil.getInt( 1 ) + "&amp;portlet_id=" +
                daoUtil.getInt( 2 ) );
            XmlUtil.addElement( strXml, TAG_PRIORITY, priority.toString(  ) );
            XmlUtil.addElement( strXml, TAG_LAST_MOD, daoUtil.getString( 3 ).substring( 0, 10 ) );
            XmlUtil.addElement( strXml, TAG_CHANGE_FREQ, strChangeFrequency );
            XmlUtil.endElement( strXml, TAG_URL );
        }

        daoUtil.free(  );
    }
}
