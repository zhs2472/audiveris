//----------------------------------------------------------------------------//
//                                                                            //
//                          S p o t s C o n t r o l l e r                     //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright © Herve Bitteur and others 2000-2013. All rights reserved.      //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.sheet;

import omr.glyph.GlyphLayer;
import omr.glyph.GlyphNest;
import omr.glyph.GlyphsModel;
import omr.glyph.Shape;
import omr.glyph.facets.Glyph;
import omr.glyph.ui.GlyphsController;
import omr.glyph.ui.NestView;
import omr.glyph.ui.SymbolGlyphBoard;

import omr.lag.Lag;

import omr.selection.MouseMovement;
import omr.selection.UserEvent;

import omr.sheet.ui.PixelBoard;

import omr.ui.BoardsPane;
import omr.ui.view.ScrollView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Class {@code SpotsController} is a quick & dirty hack to display
 * the retrieved spots.
 *
 * @author Hervé Bitteur
 */
public class SpotsController
        extends GlyphsController
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = LoggerFactory.getLogger(
            SpotsController.class);

    /** Set of shapes of interest. */
    private static final EnumSet<Shape> relevantShapes = EnumSet.of(
            Shape.BEAM_SPOT,
            Shape.HEAD_SPOT,
            Shape.BEAM,
            Shape.BEAM_2,
            Shape.BEAM_3,
            Shape.NOTEHEAD_BLACK,
            Shape.NOTEHEAD_VOID);

    //~ Instance fields --------------------------------------------------------
    private final Lag[] lags;

    /** Related user display if any */
    private MyView view;

    //~ Constructors -----------------------------------------------------------
    //-----------------//
    // SpotsController //
    //-----------------//
    /**
     * Creates a new SpotsController object.
     *
     * @param sheet related sheet
     * @param lags  collection of lags to handle
     */
    public SpotsController (Sheet sheet,
                            Lag... lags)
    {
        super(new GlyphsModel(sheet, sheet.getNest(), null));
        this.lags = lags;
    }

    //~ Methods ----------------------------------------------------------------
    //---------//
    // refresh //
    //---------//
    /**
     * Refresh the display if any, with proper colors for sections
     */
    public void refresh ()
    {
        if (view == null) {
            displayFrame();
        } else if (view != null) {
            view.refresh();
        }
    }

    //--------------//
    // displayFrame //
    //--------------//
    private void displayFrame ()
    {
        // Specific rubber display
        view = new MyView(getNest(), Arrays.asList(lags));

        sheet.getAssembly()
                .addViewTab(
                        "Spots",
                        new ScrollView(view),
                        new BoardsPane(
                        new PixelBoard(sheet),
                        new SymbolGlyphBoard(this, true, true)));
    }

    //~ Inner Classes ----------------------------------------------------------
    //--------//
    // MyView //
    //--------//
    private final class MyView
            extends NestView
    {
        //~ Constructors -------------------------------------------------------

        public MyView (GlyphNest nest,
                       List<Lag> lags)
        {
            super(nest, lags, sheet);

            setLocationService(sheet.getLocationService());

            setName("SpotController-MyView");
        }

        //~ Methods ------------------------------------------------------------
        //---------//
        // onEvent //
        //---------//
        /**
         * Call-back triggered from selection objects.
         *
         * @param event the notified event
         */
        @Override
        public void onEvent (UserEvent event)
        {
            try {
                // Ignore RELEASING
                if (event.movement == MouseMovement.RELEASING) {
                    return;
                }

                // Keep normal view behavior (rubber, etc...)
                super.onEvent(event);

                //
                //                // Additional tasks
                //                if (event instanceof LocationEvent) {
                //                    LocationEvent sheetLocation = (LocationEvent) event;
                //
                //                    if (sheetLocation.hint == SelectionHint.LOCATION_INIT) {
                //                        Rectangle rect = sheetLocation.getData();
                //
                //                        if ((rect != null) &&
                //                            (rect.width == 0) &&
                //                            (rect.height == 0)) {
                //                            // Look for pointed glyph
                //                            logger.info("Rect: {}", rect);
                //
                //                            //                            int index = glyphLookup(rect);
                //                            //                            navigator.setIndex(index, sheetLocation.hint);
                //                        }
                //                    }
                //                } else if (event instanceof GlyphEvent) {
                //                    GlyphEvent glyphEvent = (GlyphEvent) event;
                //
                //                    if (glyphEvent.hint == GLYPH_INIT) {
                //                        Glyph glyph = glyphEvent.getData();
                //
                //                        // Display glyph contour
                //                        if (glyph != null) {
                //                            locationService.publish(
                //                                new LocationEvent(
                //                                    this,
                //                                    glyphEvent.hint,
                //                                    null,
                //                                    glyph.getBounds()));
                //                        }
                //                    }
                //                }
            } catch (Exception ex) {
                logger.warn(getClass().getName() + " onEvent error", ex);
            }
        }

        //-------------//
        // renderItems //
        //-------------//
        @Override
        public void renderItems (Graphics2D g)
        {
            super.renderItems(g);

            Rectangle clip = g.getClipBounds();

            Color oldColor = g.getColor();
            g.setColor(Color.RED);

            for (GlyphLayer layer : GlyphLayer.concreteValues()) {
                for (Glyph glyph : nest.getGlyphs(layer)) {
                    final Shape shape = glyph.getShape();

                    if (relevantShapes.contains(shape)
                        && ((clip == null) || clip.intersects(glyph.getBounds()))) {
                        // Draw mean line
                        glyph.renderLine(g);
                    }
                }
            }

            g.setColor(oldColor);
        }
    }
}