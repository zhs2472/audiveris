//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                    S y m b o l F a c t o r y                                   //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//  Copyright © Herve Bitteur and others 2000-2016. All rights reserved.
//  This software is released under the GNU General Public License.
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package omr.sheet.symbol;

import omr.classifier.Evaluation;

import omr.constant.Constant;
import omr.constant.ConstantSet;

import omr.glyph.Glyph;
import omr.glyph.Shape;
import static omr.glyph.ShapeSet.*;

import omr.sheet.Scale;
import omr.sheet.Sheet;
import omr.sheet.Staff;
import omr.sheet.SystemInfo;
import omr.sheet.header.TimeBuilder;
import omr.sheet.rhythm.MeasureStack;

import omr.sig.SIGraph;
import omr.sig.inter.AbstractChordInter;
import omr.sig.inter.AbstractFlagInter;
import omr.sig.inter.AbstractHeadInter;
import omr.sig.inter.AlterInter;
import omr.sig.inter.BarlineInter;
import omr.sig.inter.ClefInter;
import omr.sig.inter.DynamicsInter;
import omr.sig.inter.FermataInter;
import omr.sig.inter.FingeringInter;
import omr.sig.inter.FretInter;
import omr.sig.inter.Inter;
import omr.sig.inter.PedalInter;
import omr.sig.inter.PluckingInter;
import omr.sig.inter.RestInter;
import omr.sig.inter.TimeNumberInter;
import omr.sig.inter.TimeWholeInter;
import omr.sig.inter.TupletInter;
import omr.sig.relation.FermataBarRelation;

import omr.util.Navigable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Class {@code SymbolFactory} generates the inter instances corresponding to
 * to an acceptable symbol evaluation in a given system.
 * <p>
 * (Generally there is one inter instance per evaluation, an exception is the case of full time
 * signature which leads to upper plus lower number instances).
 *
 * @author Hervé Bitteur
 */
public class SymbolFactory
{
    //~ Static fields/initializers -----------------------------------------------------------------

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(SymbolFactory.class);

    //~ Instance fields ----------------------------------------------------------------------------
    /** The dedicated system. */
    @Navigable(false)
    private final SystemInfo system;

    /** The related sheet. */
    @Navigable(false)
    private final Sheet sheet;

    /** Global scale. */
    private final Scale scale;

    /** The related SIG. */
    private final SIGraph sig;

    /** All system stems, ordered by abscissa. */
    private final List<Inter> systemStems;

    /** All system heads, ordered by abscissa. */
    private final List<Inter> systemHeads;

    /** All system head-based chords, ordered by abscissa. */
    private final List<Inter> systemHeadChords;

    /** All system rests, ordered by abscissa. */
    private List<Inter> systemRests;

    /** All system bar lines, ordered by abscissa. */
    private List<Inter> systemBars;

    /** Dot factory companion. */
    private final DotFactory dotFactory;

    //~ Constructors -------------------------------------------------------------------------------
    /**
     * Creates a new SymbolsFactory object.
     *
     * @param system the dedicated system
     */
    public SymbolFactory (SystemInfo system)
    {
        this.system = system;

        sig = system.getSig();
        sheet = system.getSheet();
        scale = sheet.getScale();

        systemStems = sig.inters(Shape.STEM);
        Collections.sort(systemStems, Inter.byAbscissa);

        systemHeads = sig.inters(AbstractHeadInter.class);
        Collections.sort(systemHeads, Inter.byAbscissa);

        systemHeadChords = sig.inters(AbstractChordInter.class);
        Collections.sort(systemHeadChords, Inter.byAbscissa);

        dotFactory = new DotFactory(this);
    }

    //~ Methods ------------------------------------------------------------------------------------
    //---------//
    // process //
    //---------//
    /**
     * Create the proper inter instance(s) for the provided evaluated glyph.
     * <p>
     * TODO: method to be completed so that all possible inter classes are really handled!!!!
     *
     * @param eval         evaluation result
     * @param glyph        evaluated glyph
     * @param closestStaff only the closest staff, ordinate-wise
     */
    public void create (Evaluation eval,
                        Glyph glyph,
                        Staff closestStaff)
    {
        final Shape shape = eval.shape;
        final double grade = Inter.intrinsicRatio * eval.grade;
        sheet.getGlyphIndex().register(glyph); // TODO: perhaps an overkill...

        if (glyph.isVip()) {
            logger.info("glyph#{} symbol created as {}", glyph.getId(), eval.shape);
        }

        if (Clefs.contains(shape)) {
            addSymbol(ClefInter.create(glyph, shape, grade, closestStaff)); // Staff is OK
        } else if (Rests.contains(shape)) {
            RestInter rest = RestInter.create(glyph, shape, grade, system, systemHeadChords);

            if (rest != null) {
                addSymbol(rest);
            }
        } else if (Alterations.contains(shape)) {
            AlterInter alterInter = AlterInter.create(glyph, shape, grade, closestStaff); // Staff is very questionable!
            addSymbol(alterInter);
            alterInter.detectNoteRelation(systemHeads);
        } else if (Flags.contains(shape)) {
            AbstractFlagInter.create(glyph, shape, grade, system, systemStems); // Glyph is checked
        } else if (PartialTimes.contains(shape)) {
            TimeNumberInter timeNumberInter = TimeNumberInter.create(
                    glyph,
                    shape,
                    grade,
                    closestStaff); // Staff is OK

            if (timeNumberInter != null) {
                addSymbol(timeNumberInter);
            }
        } else if (WholeTimes.contains(shape)) {
            TimeWholeInter time = new TimeWholeInter(glyph, shape, grade);
            time.setStaff(closestStaff); // Staff is OK
            addSymbol(time);
        } else if (Dynamics.contains(shape)) {
            addSymbol(new DynamicsInter(glyph, shape, grade));
        } else if (Tuplets.contains(shape)) {
            TupletInter tuplet = TupletInter.create(glyph, shape, grade, system, systemHeadChords);

            if (tuplet != null) {
                addSymbol(tuplet);
            }
        } else if (Fermatas.contains(shape)) {
            FermataInter fermata = FermataInter.create(glyph, shape, grade, system);

            if (fermata != null) {
                addSymbol(fermata);
                fermata.linkWithBarline();
            }
        } else if (Pedals.contains(shape)) {
            addSymbol(new PedalInter(glyph, shape, grade));
        } else if (shape == Shape.DOT_set) {
            dotFactory.instantDotChecks(eval, glyph);
        } else if (constants.supportFingerings.isSet() && Digits.contains(shape)) {
            addSymbol(FingeringInter.create(glyph, shape, grade));
        } else if (constants.supportFrets.isSet() && Romans.contains(shape)) {
            addSymbol(FretInter.create(glyph, shape, grade));
        } else if (constants.supportPluckings.isSet() && Pluckings.contains(shape)) {
            addSymbol(PluckingInter.create(glyph, shape, grade));
        }
    }

    //-----------//
    // getSystem //
    //-----------//
    public SystemInfo getSystem ()
    {
        return system;
    }

    //---------------//
    // getSystemBars //
    //---------------//
    public List<Inter> getSystemBars ()
    {
        if (systemBars == null) {
            systemBars = sig.inters(BarlineInter.class);
            Collections.sort(systemBars, Inter.byAbscissa);
        }

        return systemBars;
    }

    //----------------//
    // getSystemRests //
    //----------------//
    public List<Inter> getSystemRests ()
    {
        if (systemRests == null) {
            systemRests = sig.inters(RestInter.class);
            Collections.sort(systemRests, Inter.byAbscissa);
        }

        return systemRests;
    }

    //-------------//
    // linkSymbols //
    //-------------//
    public void linkSymbols ()
    {
        // Handle fermata relationships
        linkFermatas();

        // Conflicting dot interpretations
        dotFactory.lateDotChecks();

        // Column consistency of Time Signatures in a system
        handleTimes();
    }

    //---------------------//
    // getSystemHeadChords //
    //---------------------//
    List<Inter> getSystemHeadChords ()
    {
        return systemHeadChords;
    }

    //----------------//
    // getSystemHeads //
    //----------------//
    List<Inter> getSystemHeads ()
    {
        return systemHeads;
    }

    //-----------//
    // addSymbol //
    //-----------//
    /**
     * Add the provided inter to the SIG, and make sure its glyph if any is registered.
     *
     * @param inter the created inter to add to SIG
     */
    private void addSymbol (Inter inter)
    {
        Glyph glyph = inter.getGlyph();

        if ((glyph != null) && (glyph.getId() == null)) {
            sheet.getGlyphIndex().register(glyph);
        }

        sig.addVertex(inter);
    }

    //-------------//
    // handleTimes //
    //-------------//
    /**
     * Handle time symbols outside of system header.
     * Isolated time symbols found outside of system header lead to the retrieval of a column of
     * time signatures.
     */
    private void handleTimes ()
    {
        // Retrieve all time symbols (outside staff headers)
        List<Inter> systemTimes = sig.inters(
                new Class[]{TimeWholeInter.class, // Whole symbol like C or 6/8
                            TimeNumberInter.class}); // Partial symbol like 6 or 8
        List<Inter> toDelete = new ArrayList<Inter>();

        for (Inter inter : systemTimes) {
            Staff staff = inter.getStaff();

            if (inter.getCenter().x < staff.getHeaderStop()) {
                toDelete.add(inter);
            }
        }

        systemTimes.removeAll(toDelete);

        if (systemTimes.isEmpty()) {
            return;
        }

        // Dispatch these time symbols into their containing stack
        Map<MeasureStack, Set<Inter>> timeMap = new TreeMap<MeasureStack, Set<Inter>>(
                new Comparator<MeasureStack>()
        {
            @Override
            public int compare (MeasureStack s1,
                                MeasureStack s2)
            {
                return Integer.compare(s1.getIdValue(), s2.getIdValue());
            }
        });

        for (Inter inter : systemTimes) {
            MeasureStack stack = system.getMeasureStackAt(inter.getCenter());
            Set<Inter> stackSet = timeMap.get(stack);

            if (stackSet == null) {
                timeMap.put(stack, stackSet = new HashSet<Inter>());
            }

            stackSet.add(inter);
        }

        // Finally, scan each relevant stack
        for (Entry<MeasureStack, Set<Inter>> entry : timeMap.entrySet()) {
            new TimeBuilder.BasicColumn(entry.getKey(), entry.getValue()).retrieveTime();
        }
    }

    //--------------//
    // linkFermatas //
    //--------------//
    /**
     * Try to link any fermata with chords (head or rest) or barline.
     * If not successful, the fermata candidate is deleted.
     */
    private void linkFermatas ()
    {
        List<Inter> fermatas = sig.inters(FermataInter.class);

        for (Inter inter : fermatas) {
            FermataInter fermata = (FermataInter) inter;

            if (fermata.isVip()) {
                logger.info("VIP linkFermatas on {}", fermata);
            }

            // Look for a chord (head or rest) related to this fermata
            final Point center = fermata.getCenter();
            final MeasureStack stack = system.getMeasureStackAt(center);
            final Collection<AbstractChordInter> chords = (fermata.getShape() == Shape.FERMATA_BELOW)
                    ? stack.getStandardChordsAbove(center)
                    : stack.getStandardChordsBelow(center);

            if (!fermata.linkWithChords(chords)) {
                // Check whether this fermata has a link to a barline
                if (!sig.hasRelation(fermata, FermataBarRelation.class)) {
                    fermata.delete(); // No link at all
                }
            }
        }
    }

    //~ Inner Classes ------------------------------------------------------------------------------
    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
            extends ConstantSet
    {
        //~ Instance fields ------------------------------------------------------------------------

        private final Constant.Boolean supportPluckings = new Constant.Boolean(
                false,
                "Should we support Pluckings (guitar right-hand)?");

        private final Constant.Boolean supportFingerings = new Constant.Boolean(
                false,
                "Should we support Fingerings (guitar left-hand)?");

        private final Constant.Boolean supportFrets = new Constant.Boolean(
                false,
                "Should we support Fret indications (guitar left-hand)?");
    }
}