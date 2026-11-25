/*
 * Copyright (c) 2002-2026, Mairie de Paris
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.paris.lutece.portal.business.page.Page;
import fr.paris.lutece.portal.service.page.IPageService;
import fr.paris.lutece.portal.service.portal.PortalService;
import fr.paris.lutece.portal.service.security.LuteceAuthentication;
import fr.paris.lutece.portal.service.security.SecurityService;
import fr.paris.lutece.test.LuteceTestCase;
import jakarta.inject.Inject;

/**
 * The sitemap is built by a daemon, without request nor user: the pages restricted to a role are left out instead of
 * stopping the generation.
 */
public class SiteMapServiceTest extends LuteceTestCase
{
    private static final int TEMPLATE_ONE_COLUMN = 2;

    @Inject
    private IPageService _pageService;

    private int _nPublicPage;
    private int _nRestrictedPage;
    private int _nChildOfRestrictedPage;

    @BeforeEach
    @Override
    protected void setUp( ) throws Exception
    {
        super.setUp( );
        _nPublicPage = createPage( PortalService.getRootPageId( ), Page.ROLE_NONE );
        _nRestrictedPage = createPage( PortalService.getRootPageId( ), "sitemap_test_role" );
        _nChildOfRestrictedPage = createPage( _nRestrictedPage, Page.ROLE_NONE );
    }

    @AfterEach
    @Override
    protected void tearDown( ) throws Exception
    {
        _pageService.removePage( _nChildOfRestrictedPage );
        _pageService.removePage( _nRestrictedPage );
        _pageService.removePage( _nPublicPage );
        super.tearDown( );
    }

    @Test
    public void testRestrictedPageWithAnExternalAuthentication( ) throws ReflectiveOperationException
    {
        Field fieldService = SecurityService.class.getDeclaredField( "_authenticationService" );
        Field fieldEnable = SecurityService.class.getDeclaredField( "_bEnable" );
        fieldService.setAccessible( true );
        fieldEnable.setAccessible( true );
        Object previousService = fieldService.get( null );
        boolean bPreviousEnable = fieldEnable.getBoolean( null );
        // an external authentication (as MultiLuteceAuthentication of MyLutece): no user without request
        LuteceAuthentication authentication = (LuteceAuthentication) Proxy.newProxyInstance( getClass( ).getClassLoader( ), new Class<?> [ ] {
                LuteceAuthentication.class
        }, ( proxy, method, args ) -> {
            switch( method.getName( ) )
            {
                case "isExternalAuthentication":
                case "isMultiAuthenticationSupported":
                    return Boolean.TRUE;
                case "hashCode":
                    return System.identityHashCode( proxy );
                case "equals":
                    return proxy == args [0];
                default:
                    return method.getReturnType( ) == boolean.class ? Boolean.FALSE : null;
            }
        } );
        fieldService.set( null, authentication );
        fieldEnable.setBoolean( null, true );

        try
        {
            String strXml = SiteMapService.getXmlSiteMap( );

            assertTrue( strXml.contains( "page_id=" + _nPublicPage + "<" ), "the public page should be in the sitemap" );
            assertFalse( strXml.contains( "page_id=" + _nRestrictedPage + "<" ), "the page restricted to a role should not be in the sitemap" );
            assertFalse( strXml.contains( "page_id=" + _nChildOfRestrictedPage + "<" ), "the pages under a restricted page should not be in the sitemap" );
        }
        finally
        {
            fieldService.set( null, previousService );
            fieldEnable.setBoolean( null, bPreviousEnable );
        }
    }

    @Test
    public void testRestrictedPageWithoutAuthentication( )
    {
        String strXml = SiteMapService.getXmlSiteMap( );

        assertTrue( strXml.contains( "page_id=" + _nPublicPage + "<" ), "the public page should be in the sitemap" );
        // the core shows every page when the front office authentication is off (Page.isVisible)
        assertTrue( strXml.contains( "page_id=" + _nRestrictedPage + "<" ), "without authentication a page with a role is public" );
    }

    private int createPage( int nParentId, String strRole )
    {
        Page page = new Page( );
        page.setParentPageId( nParentId );
        page.setPageTemplateId( TEMPLATE_ONE_COLUMN );
        page.setName( "SiteMapServiceTest" );
        page.setDescription( "SiteMapServiceTest" );
        page.setRole( strRole );
        _pageService.createPage( page );

        return page.getId( );
    }
}
