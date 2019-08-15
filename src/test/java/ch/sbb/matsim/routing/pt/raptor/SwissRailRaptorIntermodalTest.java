/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.router.TripRouter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.pt.PtConstants;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
public class SwissRailRaptorIntermodalTest {

    @Test
    public void testIntermodalTrip() {
        IntermodalFixture f = new IntermodalFixture();

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
            new TeleportationRoutingModule(TransportMode.walk, f.scenario, 1.1, 1.3));
        routingModules.put(TransportMode.bike,
            new TeleportationRoutingModule(TransportMode.bike, f.scenario, 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        walkAccess.setInitialSearchRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);
        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(1500);
        bikeAccess.setInitialSearchRadius(1500);
        bikeAccess.setStopFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setStopFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), stopFinder, null );

        Facility fromFac = new FakeFacility(new Coord(10000, 10500), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(50000, 10500), Id.create("to", Link.class));

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7*3600, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals("wrong number of legs.", 5, legs.size());
        Leg leg = legs.get(0);
        Assert.assertEquals(TransportMode.bike, leg.getMode());
        Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("bike_3", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(1);
        Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
        Assert.assertEquals(Id.create("bike_3", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(2);
        Assert.assertEquals(TransportMode.pt, leg.getMode());
        Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(3);
        Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
        Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("bike_5", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(4);
        Assert.assertEquals(TransportMode.bike, leg.getMode());
        Assert.assertEquals(Id.create("bike_5", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
    }

    @Test
    public void testIntermodalTrip_TripRouterIntegration() {
        IntermodalFixture f = new IntermodalFixture();

        RoutingModule walkRoutingModule = new TeleportationRoutingModule(TransportMode.walk, f.scenario, 1.1, 1.3);
        RoutingModule bikeRoutingModule = new TeleportationRoutingModule(TransportMode.bike, f.scenario, 3, 1.4);

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk, walkRoutingModule);
        routingModules.put(TransportMode.bike, bikeRoutingModule);

        TripRouter.Builder tripRouterBuilder = new TripRouter.Builder(f.config)
        		.setRoutingModule(TransportMode.walk, walkRoutingModule)
        		.setRoutingModule(TransportMode.bike, bikeRoutingModule);
        
        TripRouter tripRouter = tripRouterBuilder.build();

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        walkAccess.setInitialSearchRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);
        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(1500);
        bikeAccess.setInitialSearchRadius(1500);
        bikeAccess.setStopFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setStopFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
            new LeastCostRaptorRouteSelector(), stopFinder, null );

        RoutingModule ptRoutingModule = new SwissRailRaptorRoutingModule(raptor, f.scenario.getTransitSchedule(), f.scenario.getNetwork(), walkRoutingModule);
        tripRouterBuilder.setRoutingModule(TransportMode.pt, ptRoutingModule);
        tripRouter = tripRouterBuilder.build();

        Facility fromFac = new FakeFacility(new Coord(10000, 10500), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(50000, 10500), Id.create("to", Link.class));

        List<? extends PlanElement> planElements = tripRouter.calcRoute(TransportMode.pt, fromFac, toFac, 7*3600, f.dummyPerson);

        for (PlanElement pe : planElements) {
            System.out.println(pe);
        }

        Assert.assertEquals("wrong number of PlanElements.", 9, planElements.size());
        Assert.assertTrue(planElements.get(0) instanceof Leg);
        Assert.assertTrue(planElements.get(1) instanceof Activity);
        Assert.assertTrue(planElements.get(2) instanceof Leg);
        Assert.assertTrue(planElements.get(3) instanceof Activity);
        Assert.assertTrue(planElements.get(4) instanceof Leg);
        Assert.assertTrue(planElements.get(5) instanceof Activity);
        Assert.assertTrue(planElements.get(6) instanceof Leg);
        Assert.assertTrue(planElements.get(7) instanceof Activity);
        Assert.assertTrue(planElements.get(8) instanceof Leg);

        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(1)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(3)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(5)).getType());
        Assert.assertEquals(PtConstants.TRANSIT_ACTIVITY_TYPE, ((Activity) planElements.get(7)).getType());

        Assert.assertEquals(TransportMode.bike, ((Leg) planElements.get(0)).getMode());
        Assert.assertEquals(TransportMode.transit_walk, ((Leg) planElements.get(2)).getMode());
        Assert.assertEquals(TransportMode.pt, ((Leg) planElements.get(4)).getMode());
        Assert.assertEquals(TransportMode.transit_walk, ((Leg) planElements.get(6)).getMode());
        Assert.assertEquals(TransportMode.bike, ((Leg) planElements.get(8)).getMode());

        Assert.assertEquals(0.0, ((Activity) planElements.get(1)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(3)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(5)).getMaximumDuration(), 0.0);
        Assert.assertEquals(0.0, ((Activity) planElements.get(7)).getMaximumDuration(), 0.0);
    }

    @Test
    public void testIntermodalTrip_walkOnlyNoSubpop() {
        IntermodalFixture f = new IntermodalFixture();

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(-8.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(-8.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario, 1.1, 1.3));
        routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario, 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(1000);
        walkAccess.setInitialSearchRadius(1000);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
            new LeastCostRaptorRouteSelector(), stopFinder, null );

        Facility fromFac = new FakeFacility(new Coord(10000, 10500), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(50000, 10500), Id.create("to", Link.class));

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7*3600, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals("wrong number of legs.", 3, legs.size());
        Leg leg = legs.get(0);
        Assert.assertEquals(TransportMode.access_walk, leg.getMode());
        Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("pt_2", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(1);
        Assert.assertEquals(TransportMode.pt, leg.getMode());
        Assert.assertEquals(Id.create("pt_2", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
        leg = legs.get(2);
        Assert.assertEquals(TransportMode.egress_walk, leg.getMode());
        Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
    }

    /**
     * Test that if start and end are close to each other, such that the intermodal
     * access and egress go to/from the same stop, still a direct transit_walk is returned.
     * 
     * Aug'19: The pt router shall no longer return direct walks, so check instead that the
     * correct intermodal trip via a transit stop is returned.
     */
    @Test
    public void testIntermodalTrip_withoutPt() {
        IntermodalFixture f = new IntermodalFixture();

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0.0);
        f.config.planCalcScore().addModeParams(egressWalk);

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario, 1.1, 1.3));
        routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario, 3, 1.4));

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(1200);
        bikeAccess.setInitialSearchRadius(1200);
        bikeAccess.setStopFilterAttribute("bikeAccessible");
        bikeAccess.setLinkIdAttribute("accessLinkId_bike");
        bikeAccess.setStopFilterValue("true");
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
            new LeastCostRaptorRouteSelector(), stopFinder, null );

        Facility fromFac = new FakeFacility(new Coord(10000, 9000), Id.create("from", Link.class));
        Facility toFac = new FakeFacility(new Coord(11000, 11000), Id.create("to", Link.class));

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7*3600, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }
        
        // As the router shall no longer give back direct walks, the right solution would now be
        // bike to bike network link next to transit stop -> non_network_walk to transit stop -> 
        // non_network_walk back to bike network link -> bike to destination
        
        // closest pt stop is stop 3 at 10500, 10000
        Coord ptStop3 = f.scenario.getTransitSchedule().getFacilities().get(Id.create("3", TransitStopFacility.class)).getCoord();

        Assert.assertEquals("wrong number of legs.", 4, legs.size());
        Leg leg = legs.get(0);
        Assert.assertEquals(TransportMode.bike, leg.getMode());
        Assert.assertEquals(CoordUtils.calcEuclideanDistance(fromFac.getCoord(), ptStop3)*1.4, leg.getRoute().getDistance(), 1e-7);
        Assert.assertEquals(Id.createLinkId("from"), leg.getRoute().getStartLinkId());
        // TODO: the transit stop facilities in the intermodal fixture are located on links which are very far away from the stop
        // coordinate. This looks weird, e.g. stop "3" is at 10500, 10000 but on link "pt_3" fromNode 25000, 10000 toNode 30000, 10000
        // This seems to cause the weird choice of bike links next to the station ("bike_3" fromNode 25000, 10000 toNode 30000, 10000)
        // However the distances are right.
        // For the time being accept that and check that these start/end link ids are returned. - gleich aug'19
        Assert.assertEquals(Id.createLinkId("bike_3"), leg.getRoute().getEndLinkId());
        leg = legs.get(1);
        // walk from bike network link next to stop to transit stop link
        Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
        Assert.assertEquals(0, leg.getRoute().getDistance(), 1e-7);
        Assert.assertEquals(Id.createLinkId("bike_3"), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.createLinkId("pt_3"), leg.getRoute().getEndLinkId());
        leg = legs.get(2);
        // walk back from transit stop link to bike network link
        Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
        Assert.assertEquals(0, leg.getRoute().getDistance(), 1e-7);
        Assert.assertEquals(Id.createLinkId("pt_3"), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.createLinkId("bike_3"), leg.getRoute().getEndLinkId());
        leg = legs.get(3);
        Assert.assertEquals(TransportMode.bike, leg.getMode());
        Assert.assertEquals(CoordUtils.calcEuclideanDistance(ptStop3, toFac.getCoord())*1.4, leg.getRoute().getDistance(), 1e-7);
        Assert.assertEquals(Id.createLinkId("bike_3"), leg.getRoute().getStartLinkId());
        Assert.assertEquals(Id.createLinkId("to"), leg.getRoute().getEndLinkId());
    }

    @Test
    public void testIntermodalTrip_competingAccess() {
        IntermodalFixture f = new IntermodalFixture();

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario, 1.1, 1.3));
        routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario, 3, 1.4));

        // we need to set special values for walk and bike as the defaults are the same for walk, bike and waiting
        // which would result in all options having the same cost in the end.
        f.config.planCalcScore().getModes().get(TransportMode.walk).setMarginalUtilityOfTraveling(-7);
        f.config.planCalcScore().getModes().get(TransportMode.bike).setMarginalUtilityOfTraveling(-8);

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(egressWalk);

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(100); // force to nearest stops
        walkAccess.setInitialSearchRadius(100);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);

        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(100); // force to nearest stops
        bikeAccess.setInitialSearchRadius(100);
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        Facility fromFac = new FakeFacility(new Coord(10500, 10050), Id.create("from", Link.class)); // stop 3
        Facility toFac = new FakeFacility(new Coord(50000, 10050), Id.create("to", Link.class)); // stop 5

        // first check: bike should be the better option
        {
            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), stopFinder, null );

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
        }

        // second check: decrease bike speed, walk should be the better option
        // do the test this way to insure it is not accidentally correct due to the accidentally correct order the modes are initialized
        {
            routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario, 1.0, 1.4));

            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), stopFinder, null );

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.access_walk, leg.getMode());
            Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.egress_walk, leg.getMode());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
        }
    }

    // Checks RandomAccessEgressModeRaptorStopFinder. The desired result is that the StopFinder will try out the different
    // access/egress modes, regardless of the modes' freespeeds.
    @Test
    public void testIntermodalTrip_RandomAccessEgressModeRaptorStopFinder() {
        IntermodalFixture f = new IntermodalFixture();

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario, 1.1, 1.3));
        routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario, 3, 1.4));

        // we need to set special values for walk and bike as the defaults are the same for walk, bike and waiting
        // which would result in all options having the same cost in the end.
        f.config.planCalcScore().getModes().get(TransportMode.walk).setMarginalUtilityOfTraveling(-7);
        f.config.planCalcScore().getModes().get(TransportMode.bike).setMarginalUtilityOfTraveling(-8);

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(egressWalk);

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setRadius(100); // force to nearest stops
        f.srrConfig.addIntermodalAccessEgress(walkAccess);

        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setRadius(100); // force to nearest stops
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        Facility fromFac = new FakeFacility(new Coord(10500, 10050), Id.create("from", Link.class)); // stop 3
        Facility toFac = new FakeFacility(new Coord(50000, 10050), Id.create("to", Link.class)); // stop 5

        {

            int numWalkWalk = 0 ;
            int numWalkBike = 0 ;
            int numBikeWalk = 0 ;
            int numBikeBike = 0 ;
            int numOther = 0 ;

            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
            RandomAccessEgressModeRaptorStopFinder stopFinder = new RandomAccessEgressModeRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);


            for (int i = 0; i < 1000; i++) {
                SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                        new LeastCostRaptorRouteSelector(), stopFinder, null );

                List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);

                { // Test 1: Checks whether the amount of legs is correct, whether the legs have the correct modes,
                  // and whether the legs start and end on the correct links
                    Assert.assertEquals("wrong number of legs.", 3, legs.size());
                    Leg leg = legs.get(0);
                    Assert.assertTrue((leg.getMode().equals(TransportMode.bike)) || (leg.getMode().equals(TransportMode.access_walk)));
                    Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
                    Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
                    leg = legs.get(1);
                    Assert.assertEquals(TransportMode.pt, leg.getMode());
                    Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
                    Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
                    leg = legs.get(2);
                    Assert.assertTrue((leg.getMode().equals(TransportMode.bike)) || (leg.getMode().equals(TransportMode.egress_walk)));
                    Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
                    Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
                }

                { // Test 2: Counts all different access/egress mode combinations. The assertions occur later.


                    if ((legs.get(0).getMode().equals(TransportMode.access_walk)) && (legs.get(2).getMode().equals(TransportMode.egress_walk)))
                        numWalkWalk ++ ;
                    else if ((legs.get(0).getMode().equals(TransportMode.access_walk)) && (legs.get(2).getMode().equals(TransportMode.bike)))
                        numWalkBike ++ ;
                    else if ((legs.get(0).getMode().equals(TransportMode.bike)) && (legs.get(2).getMode().equals(TransportMode.egress_walk)))
                        numBikeWalk ++ ;
                    else if ((legs.get(0).getMode().equals(TransportMode.bike)) && (legs.get(2).getMode().equals(TransportMode.bike)))
                        numBikeBike ++ ;
                    else
                        numOther ++ ;
                }
            }

            { // Test 2: Tests whether Router chooses all 4 combinations of walk and bike. Also checks that no other
              // combination is present.
                Assert.assertTrue(numWalkWalk > 0);
                Assert.assertTrue(numWalkBike > 0);
                Assert.assertTrue(numBikeWalk > 0);
                Assert.assertTrue(numBikeBike > 0);
                Assert.assertTrue(numOther == 0);

            }
        }
    }


    /**
     * based on testIntermodalTrip_competingAccess. Bike would be fastest, but is only allowed for Direction.ACCESS.
     */
    @Test
    public void testIntermodalTrip_ParamDirection() {
        IntermodalFixture f = new IntermodalFixture();

        Map<String, RoutingModule> routingModules = new HashMap<>();
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, f.scenario, 1.1, 1.3));
        routingModules.put(TransportMode.bike,
                new TeleportationRoutingModule(TransportMode.bike, f.scenario, 3, 1.4));

        // we need to set special values for walk and bike as the defaults are the same for walk, bike and waiting
        // which would result in all options having the same cost in the end.
        f.config.planCalcScore().getModes().get(TransportMode.walk).setMarginalUtilityOfTraveling(-7);
        f.config.planCalcScore().getModes().get(TransportMode.bike).setMarginalUtilityOfTraveling(-8);

        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
        accessWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(accessWalk);
        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
        transitWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(transitWalk);
        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
        egressWalk.setMarginalUtilityOfTraveling(0);
        f.config.planCalcScore().addModeParams(egressWalk);

        f.srrConfig.setUseIntermodalAccessEgress(true);
        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
        walkAccess.setMode(TransportMode.walk);
        walkAccess.setDirections("ACCESS,EGRESS");
        walkAccess.setRadius(100); // force to nearest stops
        walkAccess.setInitialSearchRadius(100);
        f.srrConfig.addIntermodalAccessEgress(walkAccess);

        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
        bikeAccess.setMode(TransportMode.bike);
        bikeAccess.setDirections("ACCESS,EGRESS");
        bikeAccess.setRadius(100); // force to nearest stops
        bikeAccess.setInitialSearchRadius(100);
        f.srrConfig.addIntermodalAccessEgress(bikeAccess);

        Facility fromFac = new FakeFacility(new Coord(10500, 10050), Id.create("from", Link.class)); // stop 3
        Facility toFac = new FakeFacility(new Coord(50000, 10050), Id.create("to", Link.class)); // stop 5
        
        // first check: bike should be the better option
        {
            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), stopFinder, null );

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
        }

        bikeAccess.setDirections("ACCESS"); // not allowed for EGRESS
        // second check: bike is only allowed for Direction.ACCESS.
        {
            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), stopFinder, null );

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.egress_walk, leg.getMode()); // TODO: should be TransportMode.walk?
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
        }
        
        bikeAccess.setDirections("EGRESS"); // not allowed for ACCESS
        // third check: bike is now only allowed for Direction.EGRESS.
        {
            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
                new LeastCostRaptorRouteSelector(), stopFinder, null );

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.access_walk, leg.getMode()); // TODO: should be TransportMode.walk?
            Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
        }
    }

    /**
     * Tests the following situation: two stops A and B close to each other, A has intermodal access, B not.
     * The route is fastest from B to C, with intermodal access to A and then transferring from A to B.
     * Make sure that in such cases the correct transit_walks are generated around stops A and B for access to pt.
     */
    @Test
    public void testIntermodalTrip_accessTransfer() {
        IntermodalTransferFixture f = new IntermodalTransferFixture();

        Facility fromFac = new FakeFacility(new Coord(10000, 500), Id.create("from", Link.class)); // stop B or C
        Facility toFac = new FakeFacility(new Coord(20000, 100), Id.create("to", Link.class)); // stop D

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), f.routingModules);
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
            new LeastCostRaptorRouteSelector(), stopFinder, null );

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 8 * 3600 - 900, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals(5, legs.size());

        Leg legBike = legs.get(0);
        Assert.assertEquals("bike", legBike.getMode());
        Assert.assertEquals("from", legBike.getRoute().getStartLinkId().toString());
        Assert.assertEquals("bike_B", legBike.getRoute().getEndLinkId().toString());

        Leg legTransfer = legs.get(1);
        Assert.assertEquals("transit_walk", legTransfer.getMode());
        Assert.assertEquals("bike_B", legTransfer.getRoute().getStartLinkId().toString());
        Assert.assertEquals("BB", legTransfer.getRoute().getEndLinkId().toString());

        Leg legTransfer2 = legs.get(2);
        Assert.assertEquals("transit_walk", legTransfer2.getMode());
        Assert.assertEquals("BB", legTransfer2.getRoute().getStartLinkId().toString());
        Assert.assertEquals("CC", legTransfer2.getRoute().getEndLinkId().toString());

        Leg legPT = legs.get(3);
        Assert.assertEquals("pt", legPT.getMode());
        Assert.assertEquals("CC", legPT.getRoute().getStartLinkId().toString());
        Assert.assertEquals("DD", legPT.getRoute().getEndLinkId().toString());

        Leg legAccess = legs.get(4);
        Assert.assertEquals(TransportMode.non_network_walk, legAccess.getMode());
        Assert.assertEquals("DD", legAccess.getRoute().getStartLinkId().toString());
        Assert.assertEquals("to", legAccess.getRoute().getEndLinkId().toString());
    }

    /**
     * When using intermodal access/egress, transfers at the beginning are allowed to
     * be able to transfer from a stop with intermodal access/egress to another stop
     * with better connections to the destination. Earlier versions of SwissRailRaptor
     * had a bug that resulted in only stops where such transfers were possible to be
     * used for route finding, but not stops directly reachable and usable.
     * This test tries to cover this case to make sure, route finding works as expected
     * in all cases.
     */
    @Test
    public void testIntermodalTrip_singleReachableStop() {
        IntermodalTransferFixture f = new IntermodalTransferFixture();

        f.srrConfig.getIntermodalAccessEgressParameterSets().removeIf(paramset -> paramset.getMode().equals("bike")); // we only want "walk" as mode

        Facility fromFac = new FakeFacility(new Coord(9800, 400), Id.create("from", Link.class)); // stops B or E, B is intermodal and triggered the bug
        Facility toFac = new FakeFacility(new Coord(20000, 5100), Id.create("to", Link.class)); // stop F

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), f.routingModules);
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
            new LeastCostRaptorRouteSelector(), stopFinder, null);

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7.5 * 3600 + 900, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals(3, legs.size());

        Leg legAccess = legs.get(0);
        Assert.assertEquals(TransportMode.non_network_walk, legAccess.getMode());
        Assert.assertEquals("from", legAccess.getRoute().getStartLinkId().toString());
        Assert.assertEquals("EE", legAccess.getRoute().getEndLinkId().toString());

        Leg legPT = legs.get(1);
        Assert.assertEquals(TransportMode.pt, legPT.getMode());
        Assert.assertEquals("EE", legPT.getRoute().getStartLinkId().toString());
        Assert.assertEquals("FF", legPT.getRoute().getEndLinkId().toString());

        Leg legEgress = legs.get(2);
        Assert.assertEquals(TransportMode.non_network_walk, legEgress.getMode());
        Assert.assertEquals("FF", legEgress.getRoute().getStartLinkId().toString());
        Assert.assertEquals("to", legEgress.getRoute().getEndLinkId().toString());

    }

    @Test
    public void testIntermodalTrip_egressTransfer() {
        IntermodalTransferFixture f = new IntermodalTransferFixture();

        Facility fromFac = new FakeFacility(new Coord(20000, 100), Id.create("from", Link.class)); // stop D
        Facility toFac = new FakeFacility(new Coord(10000, 500), Id.create("to", Link.class)); // stop B or C

        SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
        DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), f.routingModules);
        SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
            new LeastCostRaptorRouteSelector(), stopFinder, null );

        List<Leg> legs = raptor.calcRoute(fromFac, toFac, 8 * 3600 - 900, f.dummyPerson);
        for (Leg leg : legs) {
            System.out.println(leg);
        }

        Assert.assertEquals(5, legs.size());

        Leg legAccess = legs.get(0);
        Assert.assertEquals(TransportMode.non_network_walk, legAccess.getMode());
        Assert.assertEquals("from", legAccess.getRoute().getStartLinkId().toString());
        Assert.assertEquals("DD", legAccess.getRoute().getEndLinkId().toString());

        Leg legPT = legs.get(1);
        Assert.assertEquals("pt", legPT.getMode());
        Assert.assertEquals("DD", legPT.getRoute().getStartLinkId().toString());
        Assert.assertEquals("CC", legPT.getRoute().getEndLinkId().toString());

        Leg legTransfer = legs.get(2);
        Assert.assertEquals("transit_walk", legTransfer.getMode());
        Assert.assertEquals("CC", legTransfer.getRoute().getStartLinkId().toString());
        Assert.assertEquals("BB", legTransfer.getRoute().getEndLinkId().toString());

        Leg legTransfer2 = legs.get(3);
        Assert.assertEquals("transit_walk", legTransfer2.getMode());
        Assert.assertEquals("BB", legTransfer2.getRoute().getStartLinkId().toString());
        Assert.assertEquals("bike_B", legTransfer2.getRoute().getEndLinkId().toString());

        Leg legBike = legs.get(4);
        Assert.assertEquals("bike", legBike.getMode());
        Assert.assertEquals("bike_B", legBike.getRoute().getStartLinkId().toString());
        Assert.assertEquals("to", legBike.getRoute().getEndLinkId().toString());
    }

    /* for test of intermodal routing requiring transfers at the beginning or end of the pt trip,
     * the normal IntermodalFixture does not work, so create a special mini scenario here.
     */
    private static class IntermodalTransferFixture {

        final SwissRailRaptorConfigGroup srrConfig;
        final Config config;
        final Scenario scenario;
        final Person dummyPerson;
        final Map<String, RoutingModule> routingModules;

        public IntermodalTransferFixture() {
            this.srrConfig = new SwissRailRaptorConfigGroup();
            this.config = ConfigUtils.createConfig(this.srrConfig);
            this.scenario = ScenarioUtils.createScenario(this.config);

            /* Scenario:
                            (F)
                            / green
                           /
                        (E)
                 red            blue
            (A)-------(B)  (C)--------(D)
                      /     \
                   bike      no bike

               (E) is outside the transfer distance from (B) and (C)

             */

            Network network = this.scenario.getNetwork();
            NetworkFactory nf = network.getFactory();

            Node nodeA = nf.createNode(Id.create("A", Node.class), new Coord(    0, 0));
            Node nodeB = nf.createNode(Id.create("B", Node.class), new Coord( 9980, 0));
            Node nodeC = nf.createNode(Id.create("C", Node.class), new Coord(10020, 0));
            Node nodeD = nf.createNode(Id.create("D", Node.class), new Coord(20000, 0));
            Node nodeE = nf.createNode(Id.create("E", Node.class), new Coord(10000, 800));
            Node nodeF = nf.createNode(Id.create("F", Node.class), new Coord(20000, 5000));

            network.addNode(nodeA);
            network.addNode(nodeB);
            network.addNode(nodeC);
            network.addNode(nodeD);
            network.addNode(nodeE);
            network.addNode(nodeF);

            Link linkAA = nf.createLink(Id.create("AA", Link.class), nodeA, nodeA);
            Link linkAB = nf.createLink(Id.create("AB", Link.class), nodeA, nodeB);
            Link linkBA = nf.createLink(Id.create("BA", Link.class), nodeB, nodeA);
            Link linkBB = nf.createLink(Id.create("BB", Link.class), nodeB, nodeB);
            Link linkCC = nf.createLink(Id.create("CC", Link.class), nodeC, nodeC);
            Link linkCD = nf.createLink(Id.create("CD", Link.class), nodeC, nodeD);
            Link linkDC = nf.createLink(Id.create("DC", Link.class), nodeD, nodeC);
            Link linkDD = nf.createLink(Id.create("DD", Link.class), nodeD, nodeD);
            Link linkEE = nf.createLink(Id.create("EE", Link.class), nodeE, nodeE);
            Link linkEF = nf.createLink(Id.create("EF", Link.class), nodeE, nodeF);
            Link linkFE = nf.createLink(Id.create("FE", Link.class), nodeF, nodeE);
            Link linkFF = nf.createLink(Id.create("FF", Link.class), nodeF, nodeF);

            network.addLink(linkAA);
            network.addLink(linkAB);
            network.addLink(linkBA);
            network.addLink(linkBB);
            network.addLink(linkCC);
            network.addLink(linkCD);
            network.addLink(linkDC);
            network.addLink(linkDD);
            network.addLink(linkEE);
            network.addLink(linkEF);
            network.addLink(linkFE);
            network.addLink(linkFF);

            // ----

            TransitSchedule schedule = this.scenario.getTransitSchedule();
            TransitScheduleFactory sf = schedule.getFactory();

            TransitStopFacility stopA = sf.createTransitStopFacility(Id.create("A", TransitStopFacility.class), nodeA.getCoord(), false);
            TransitStopFacility stopB = sf.createTransitStopFacility(Id.create("B", TransitStopFacility.class), nodeB.getCoord(), false);
            TransitStopFacility stopC = sf.createTransitStopFacility(Id.create("C", TransitStopFacility.class), nodeC.getCoord(), false);
            TransitStopFacility stopD = sf.createTransitStopFacility(Id.create("D", TransitStopFacility.class), nodeD.getCoord(), false);
            TransitStopFacility stopE = sf.createTransitStopFacility(Id.create("E", TransitStopFacility.class), nodeE.getCoord(), false);
            TransitStopFacility stopF = sf.createTransitStopFacility(Id.create("F", TransitStopFacility.class), nodeF.getCoord(), false);

            stopB.getAttributes().putAttribute("bikeAccessible", true);
            stopB.getAttributes().putAttribute("accessLinkId_bike", "bike_B");

            stopA.setLinkId(linkAA.getId());
            stopB.setLinkId(linkBB.getId());
            stopC.setLinkId(linkCC.getId());
            stopD.setLinkId(linkDD.getId());
            stopE.setLinkId(linkEE.getId());
            stopF.setLinkId(linkFF.getId());

            schedule.addStopFacility(stopA);
            schedule.addStopFacility(stopB);
            schedule.addStopFacility(stopC);
            schedule.addStopFacility(stopD);
            schedule.addStopFacility(stopE);
            schedule.addStopFacility(stopF);

            // red transit line

            TransitLine redLine = sf.createTransitLine(Id.create("red", TransitLine.class));

            NetworkRoute networkRouteAB = RouteUtils.createLinkNetworkRouteImpl(linkAA.getId(), new Id[] { linkAB.getId() }, linkBB.getId());
            List<TransitRouteStop> stopsRedAB = new ArrayList<>(2);
            stopsRedAB.add(sf.createTransitRouteStop(stopA, Time.getUndefinedTime(), 0.0));
            stopsRedAB.add(sf.createTransitRouteStop(stopB, 600, Time.getUndefinedTime()));
            TransitRoute redABRoute = sf.createTransitRoute(Id.create("redAB", TransitRoute.class), networkRouteAB, stopsRedAB, "train");
            redABRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 7.5*3600));
            redLine.addRoute(redABRoute);

            NetworkRoute networkRouteBA = RouteUtils.createLinkNetworkRouteImpl(linkBB.getId(), new Id[] { linkBA.getId() }, linkAA.getId());
            List<TransitRouteStop> stopsRedBA = new ArrayList<>(2);
            stopsRedBA.add(sf.createTransitRouteStop(stopB, Time.getUndefinedTime(), 0.0));
            stopsRedBA.add(sf.createTransitRouteStop(stopA, 600, Time.getUndefinedTime()));
            TransitRoute redBARoute = sf.createTransitRoute(Id.create("redBA", TransitRoute.class), networkRouteBA, stopsRedBA, "train");
            redBARoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.5*3600));
            redLine.addRoute(redBARoute);

            schedule.addTransitLine(redLine);

            // blue transit line

            TransitLine blueLine = sf.createTransitLine(Id.create("blue", TransitLine.class));

            NetworkRoute networkRouteCD = RouteUtils.createLinkNetworkRouteImpl(linkCC.getId(), new Id[] { linkCD.getId() }, linkDD.getId());
            List<TransitRouteStop> stopsBlueCD = new ArrayList<>(2);
            stopsBlueCD.add(sf.createTransitRouteStop(stopC, Time.getUndefinedTime(), 0.0));
            stopsBlueCD.add(sf.createTransitRouteStop(stopD, 600, Time.getUndefinedTime()));
            TransitRoute blueCDRoute = sf.createTransitRoute(Id.create("blueCD", TransitRoute.class), networkRouteCD, stopsBlueCD, "train");
            blueCDRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8*3600));
            blueLine.addRoute(blueCDRoute);

            NetworkRoute networkRouteDC = RouteUtils.createLinkNetworkRouteImpl(linkDD.getId(), new Id[] { linkDC.getId() }, linkCC.getId());
            List<TransitRouteStop> stopsBlueDC = new ArrayList<>(2);
            stopsBlueDC.add(sf.createTransitRouteStop(stopD, Time.getUndefinedTime(), 0.0));
            stopsBlueDC.add(sf.createTransitRouteStop(stopC, 600, Time.getUndefinedTime()));
            TransitRoute blueDCRoute = sf.createTransitRoute(Id.create("blueDC", TransitRoute.class), networkRouteDC, stopsBlueDC, "train");
            blueDCRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8*3600));
            blueLine.addRoute(blueDCRoute);

            schedule.addTransitLine(blueLine);

            // green transit line

            TransitLine greenLine = sf.createTransitLine(Id.create("green", TransitLine.class));

            NetworkRoute networkRouteEF = RouteUtils.createLinkNetworkRouteImpl(linkEE.getId(), new Id[] { linkEF.getId() }, linkFF.getId());
            List<TransitRouteStop> stopsGreenEF = new ArrayList<>(2);
            stopsGreenEF.add(sf.createTransitRouteStop(stopE, Time.getUndefinedTime(), 0.0));
            stopsGreenEF.add(sf.createTransitRouteStop(stopF, 600, Time.getUndefinedTime()));
            TransitRoute greenEFRoute = sf.createTransitRoute(Id.create("greenEF", TransitRoute.class), networkRouteEF, stopsGreenEF, "train");
            greenEFRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8*3600));
            greenLine.addRoute(greenEFRoute);

            NetworkRoute networkRouteFE = RouteUtils.createLinkNetworkRouteImpl(linkDD.getId(), new Id[] { linkDC.getId() }, linkCC.getId());
            List<TransitRouteStop> stopsGreenFE = new ArrayList<>(2);
            stopsGreenFE.add(sf.createTransitRouteStop(stopF, Time.getUndefinedTime(), 0.0));
            stopsGreenFE.add(sf.createTransitRouteStop(stopE, 600, Time.getUndefinedTime()));
            TransitRoute greenFERoute = sf.createTransitRoute(Id.create("greenFE", TransitRoute.class), networkRouteFE, stopsGreenFE, "train");
            greenFERoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8*3600));
            greenLine.addRoute(greenFERoute);

            schedule.addTransitLine(greenLine);

            // ---

            this.dummyPerson = this.scenario.getPopulation().getFactory().createPerson(Id.create("dummy", Person.class));

            // ---

            this.routingModules = new HashMap<>();
            this.routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, this.scenario, 1.1, 1.3));
            this.routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, this.scenario, 1.1, 1.3));
            this.routingModules.put(TransportMode.bike,
                    new TeleportationRoutingModule(TransportMode.bike, this.scenario, 10, 1.4)); // make bike very fast

            // we need to set special values for walk and bike as the defaults are the same for walk, bike and waiting
            // which would result in all options having the same cost in the end.
            this.config.planCalcScore().getModes().get(TransportMode.walk).setMarginalUtilityOfTraveling(-7);
            this.config.planCalcScore().getModes().get(TransportMode.bike).setMarginalUtilityOfTraveling(-8);

            this.config.transitRouter().setMaxBeelineWalkConnectionDistance(150);
            
            PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams(TransportMode.transit_walk);
            transitWalk.setMarginalUtilityOfTraveling(0);
            this.config.planCalcScore().addModeParams(transitWalk);
            
			/*
			 * Prior to non_network_walk the utilities of access_walk and egress_walk were set to 0 here.
			 * non_network_walk replaced access_walk and egress_walk, so one might assume that now egress_walk should
			 * have marginalUtilityOfTraveling = 0.
			 * 
			 * However, non_network_walk also replaces walk, so the alternative access leg by *_walk without any bike
			 * leg is calculated based on marginalUtilityOfTraveling of non_network_walk. Setting
			 * marginalUtilityOfTraveling = 0 obviously makes that alternative more attractive than any option with bike
			 * could be. So set it to the utility TransportMode.walk already had before the replacement of access_walk
			 * and egress_walk by non_network_walk. This should be fine as the non_network_walk legs in the path with
			 * bike (and access / egress transfer) are rather short and thereby have little influence on the total cost.
			 * Furthermore, this is additional cost for the path including bike, so we are on the safe side with that
			 * change. - gleich aug'19
			 */
            PlanCalcScoreConfigGroup.ModeParams nonNetworkWalk = new PlanCalcScoreConfigGroup.ModeParams(TransportMode.non_network_walk);
            nonNetworkWalk.setMarginalUtilityOfTraveling(-7);
            this.config.planCalcScore().addModeParams(nonNetworkWalk);

            this.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(500);
            walkAccess.setInitialSearchRadius(500);
            this.srrConfig.addIntermodalAccessEgress(walkAccess);

            IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
            bikeAccess.setMode(TransportMode.bike);
            bikeAccess.setRadius(1000);
            bikeAccess.setInitialSearchRadius(1000);
            bikeAccess.setStopFilterAttribute("bikeAccessible");
            bikeAccess.setStopFilterValue("true");
            bikeAccess.setLinkIdAttribute("accessLinkId_bike");
            this.srrConfig.addIntermodalAccessEgress(bikeAccess);
        }
    }

    @Test
    public void testIntermodalTrip_Radii() {
        // Universal

        Facility fromFac = new FakeFacility(new Coord(0, 0), Id.create("AA", Link.class)); // stop A
        Facility toFac = new FakeFacility(new Coord(100000, 0), Id.create("XX", Link.class)); // stop X



        // *** P A R T  1 : Walk Only Tests ***

        // Test 0: Initial_Search_Radius includes no lines
        // Search_Extension_Radius is 0
        // General_Radius includes no lines
        // All lines are super fast
        // expected: agent will walk ("transit_walk") from A to X

        {
            IntermodalFixtureJakob f0 = new IntermodalFixtureJakob(20*60., 20*60., 20*60., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f0.scenario, 1000., 1.0));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f0.scenario, 1000., 1.0));

            f0.srrConfig.setUseIntermodalAccessEgress(true);
                IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
                walkAccess.setMode(TransportMode.walk);
                walkAccess.setRadius(300); // should not be limiting factor
                walkAccess.setInitialSearchRadius(300); // Includes no stops
                walkAccess.setSearchExtensionRadius(0);
                f0.srrConfig.addIntermodalAccessEgress(walkAccess);

                SwissRailRaptorData data = SwissRailRaptorData.create(f0.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f0.config), f0.scenario.getNetwork());
                DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
                SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f0.scenario.getConfig()),
                        new LeastCostRaptorRouteSelector(), stopFinder, null);

                List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f0.dummyPerson);
                for (Leg leg : legs) {
                    System.out.println(leg);
                }

                Assert.assertEquals("wrong number of legs.", 1, legs.size());
                Leg leg = legs.get(0);
                Assert.assertEquals(TransportMode.transit_walk, leg.getMode());
                Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
                Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            }


        // Test 1: Initial_Search_Radius includes B and C, line B is faster than C
        // Search_Extension_Radius includes D and E
        // General_Radius includes B, C, D, E
        // D and E are very fast, but shouldn't be chosen, since Initial_Search_Radius already has 2 entries
        // expected: B
        {
            IntermodalFixtureJakob f0 = new IntermodalFixtureJakob(10*60., 20*60., 1., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f0.scenario, 1000., 1.0));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f0.scenario, 1000., 1.0));

            f0.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(10000000); // should not be limiting factor
            walkAccess.setInitialSearchRadius(1200); // Should include stops B and C
            walkAccess.setSearchExtensionRadius(2000);
            f0.srrConfig.addIntermodalAccessEgress(walkAccess);

            SwissRailRaptorData data = SwissRailRaptorData.create(f0.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f0.config), f0.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f0.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), stopFinder, null);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f0.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());


        }


        // Test 2: Initial_Search_Radius includes B and C, line C is faster than B
        // Search_Extension_Radius includes D and E
        // General_Radius includes B, C, D, E
        // D and E are very fast, but shouldn't be chosen, since Initial_Search_Radius already has 2 entries
        // expected: C
        {
            IntermodalFixtureJakob f1 = new IntermodalFixtureJakob(20*60., 10*60., 1., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f1.scenario, 1.1, 1.3));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f1.scenario, 1000., 1.0));

            f1.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(10000000); // should not be limiting factor
            walkAccess.setInitialSearchRadius(1200); // Should include stops B and C
            walkAccess.setSearchExtensionRadius(0);
            f1.srrConfig.addIntermodalAccessEgress(walkAccess);

            SwissRailRaptorData data = SwissRailRaptorData.create(f1.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f1.config), f1.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f1.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), stopFinder, null);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f1.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("CC", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("CC", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
        }

        // Test 3: Initial_Search_Radius includes B
        // Search_Extension_Radius is 0
        // General_Radius includes B, C, D and E
        // B is slow, while other lines are fast
        // expected: B, since it is only stop included in Initial_Search_Radius + Search_Extension_Radius
        {
            IntermodalFixtureJakob f1 = new IntermodalFixtureJakob(20*60., 1., 1., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f1.scenario, 1.1, 1.3));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f1.scenario, 1000., 1.0));

            f1.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(10000000); // should not be limiting factor
            walkAccess.setInitialSearchRadius(700); // Should include stops B
            walkAccess.setSearchExtensionRadius(0);
            f1.srrConfig.addIntermodalAccessEgress(walkAccess);

            SwissRailRaptorData data = SwissRailRaptorData.create(f1.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f1.config), f1.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f1.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), stopFinder, null);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f1.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
        }

        // Test 4: Initial_Search_Radius includes B
        // Search_Extension_Radius includes C
        // General_Radius includes B, C, D and E
        // Line C is faster than line B
        // Lines D and E are super fast, but should not be chosen, since they are not within Initial_Search_Radius + Search_Extension_Radius
        // expected: C
        {
            IntermodalFixtureJakob f1 = new IntermodalFixtureJakob(20*60., 10.*60, 1., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f1.scenario, 1.1, 1.3));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f1.scenario, 1000., 1.0));

            f1.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(10000000); // should not be limiting factor
            walkAccess.setInitialSearchRadius(700); // Should include stops B
            walkAccess.setSearchExtensionRadius(500);
            f1.srrConfig.addIntermodalAccessEgress(walkAccess);

            SwissRailRaptorData data = SwissRailRaptorData.create(f1.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f1.config), f1.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f1.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), stopFinder, null);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f1.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("CC", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("CC", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
        }

        // Test 5: Initial_Search_Radius only includes B,
        // Search_Extension_Radius includes C and D
        // Line D is faster than lines B and C
        // expected: D
        {
            IntermodalFixtureJakob f1 = new IntermodalFixtureJakob(20*60., 20.*60, 10*60., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f1.scenario, 1.1, 1.3));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f1.scenario, 1000., 1.0));

            f1.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(10000000); // should not be limiting factor
            walkAccess.setInitialSearchRadius(700); // Should include stops B
            walkAccess.setSearchExtensionRadius(1000);
            f1.srrConfig.addIntermodalAccessEgress(walkAccess);

            SwissRailRaptorData data = SwissRailRaptorData.create(f1.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f1.config), f1.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f1.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), stopFinder, null);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f1.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("DD", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("DD", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
        }

        // Test 6: Initial_Search_Radius only includes B
        // Search_Extension_Radius includes C
        // General_Radius includes B
        // B is slow, all other lines are super fast
        // expected: B
        {
            IntermodalFixtureJakob f1 = new IntermodalFixtureJakob(20*60., 1., 1., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f1.scenario, 1.1, 1.3));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f1.scenario, 1000., 1.0));

            f1.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(900); // should not be limiting factor
            walkAccess.setInitialSearchRadius(700); // Should include stops B
            walkAccess.setSearchExtensionRadius(500);
            f1.srrConfig.addIntermodalAccessEgress(walkAccess);

            SwissRailRaptorData data = SwissRailRaptorData.create(f1.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f1.config), f1.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f1.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), stopFinder, null);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f1.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
        }

        // Test 7: Test Stop Filter Attributes
        // Initial_Search_Radius includes B and C and D
        // C and D have attribute "walk" as "true".
        // Search_Extension_Radius is 0
        // General_Radius includes B, C, D, E
        // B and D are faster than C
        // expected: D, since it is faster and it has correct attribute
        // Fail
        {
            IntermodalFixtureJakob f0 = new IntermodalFixtureJakob(10*60., 20*60., 10*60., 1.);

//            f0.scenario.getTransitSchedule().getFacilities().get(Id.create("C", TransitStopFacility.class)).getAttributes().putAttribute("walkAccessible", "true");
//            f0.scenario.getTransitSchedule().getFacilities().get(Id.create("D", TransitStopFacility.class)).getAttributes().putAttribute("walkAccessible", "true");

            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f0.scenario, 1000., 1.0));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f0.scenario, 1000., 1.0));

            f0.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(10000000); // should not be limiting factor
            walkAccess.setInitialSearchRadius(1700); // Should include stops B and C
            walkAccess.setSearchExtensionRadius(0); // includes D (if neccessary)
            walkAccess.setStopFilterAttribute("walkAccessible");
            walkAccess.setStopFilterValue("true");
            f0.srrConfig.addIntermodalAccessEgress(walkAccess);

            SwissRailRaptorData data = SwissRailRaptorData.create(f0.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f0.config), f0.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f0.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), stopFinder, null);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f0.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("DD", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("DD", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
            Assert.assertEquals(TransportMode.non_network_walk, leg.getMode());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());


        }





        // *** P A R T  2 : Walk & Bike Only Tests ***

        // Test 1: Initial_Search_Radius includes B and C, line B is faster than C
        // Search_Extension_Radius includes D and E
        // General_Radius includes B, C, D, E
        // D and E are very fast, but shouldn't be chosen, since Initial_Search_Radius already has 2 entries
        // expected: B
        {
            IntermodalFixtureJakob f0 = new IntermodalFixtureJakob(10*60., 20*60., 1., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f0.scenario, 5., 1.0));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f0.scenario, 5., 1.0));
            routingModules.put(TransportMode.bike,
                    new TeleportationRoutingModule(TransportMode.bike, f0.scenario, 50., 1.0));

            f0.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(10000000); // should not be limiting factor
            walkAccess.setInitialSearchRadius(1200); // Should include stops B and C
            walkAccess.setSearchExtensionRadius(2000);
            f0.srrConfig.addIntermodalAccessEgress(walkAccess);

            f0.srrConfig.setUseIntermodalAccessEgress(true);
            IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
            bikeAccess.setMode(TransportMode.bike);
            bikeAccess.setRadius(10000000); // should not be limiting factor
            bikeAccess.setInitialSearchRadius(1200); // Should include stops B and C
            bikeAccess.setSearchExtensionRadius(2000);
            f0.srrConfig.addIntermodalAccessEgress(bikeAccess);

            SwissRailRaptorData data = SwissRailRaptorData.create(f0.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f0.config), f0.scenario.getNetwork());
            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f0.scenario.getConfig()),
                    new LeastCostRaptorRouteSelector(), stopFinder, null);

            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f0.dummyPerson);
            for (Leg leg : legs) {
                System.out.println(leg);
            }

            Assert.assertEquals("wrong number of legs.", 3, legs.size());
            Leg leg = legs.get(0);
            Assert.assertEquals(TransportMode.bike, leg.getMode());
            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(1);
            Assert.assertEquals(TransportMode.pt, leg.getMode());
            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getStartLinkId());
            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
            leg = legs.get(2);
//            Assert.assertEquals(TransportMode.bike, leg.getMode());
//            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
//            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());


        }
//
//
//        IntermodalFixtureJakob f = new IntermodalFixtureJakob(600.,600., 600., 600.);
//
//        Map<String, RoutingModule> routingModules = new HashMap<>();
//        routingModules.put(TransportMode.walk,
//                new TeleportationRoutingModule(TransportMode.walk, f.scenario, 1.1, 1.3));
//        routingModules.put(TransportMode.bike,
//                new TeleportationRoutingModule(TransportMode.bike, f.scenario, 3, 1.4));
//
//        // we need to set special values for walk and bike as the defaults are the same for walk, bike and waiting
//        // which would result in all options having the same cost in the end.
//        f.config.planCalcScore().getModes().get(TransportMode.walk).setMarginalUtilityOfTraveling(-7);
//        f.config.planCalcScore().getModes().get(TransportMode.bike).setMarginalUtilityOfTraveling(-8);
//
//        PlanCalcScoreConfigGroup.ModeParams accessWalk = new PlanCalcScoreConfigGroup.ModeParams("access_walk");
//        accessWalk.setMarginalUtilityOfTraveling(0);
//        f.config.planCalcScore().addModeParams(accessWalk);
//        PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams("transit_walk");
//        transitWalk.setMarginalUtilityOfTraveling(0);
//        f.config.planCalcScore().addModeParams(transitWalk);
//        PlanCalcScoreConfigGroup.ModeParams egressWalk = new PlanCalcScoreConfigGroup.ModeParams("egress_walk");
//        egressWalk.setMarginalUtilityOfTraveling(0);
//        f.config.planCalcScore().addModeParams(egressWalk);
//
//        f.srrConfig.setUseIntermodalAccessEgress(true);
//        IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
//        walkAccess.setMode(TransportMode.walk);
//        walkAccess.setRadius(10000000); // should not be limiting factor
//        walkAccess.setInitialSearchRadius(1200); // Should include stops B and C
//        f.srrConfig.addIntermodalAccessEgress(walkAccess);
////
////        IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
////        bikeAccess.setMode(TransportMode.bike);
////        bikeAccess.setRadius(100); // force to nearest stops
////        bikeAccess.setInitialSearchRadius(100);
////        f.srrConfig.addIntermodalAccessEgress(bikeAccess);
//
//
//
//        // first check: bike should be the better option
//        {
////            f.scenario.getTransitSchedule().getTransitLines().get("BLine").
//
//            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
//            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
//            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
//                    new LeastCostRaptorRouteSelector(), stopFinder, null );
//
//            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
//            for (Leg leg : legs) {
//                System.out.println(leg);
//            }
//
//            Assert.assertEquals("wrong number of legs.", 3, legs.size());
//            Leg leg = legs.get(0);
//            Assert.assertEquals(TransportMode.access_walk, leg.getMode());
//            Assert.assertEquals(Id.create("AA", Link.class), leg.getRoute().getStartLinkId());
//            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getEndLinkId());
//            leg = legs.get(1);
//            Assert.assertEquals(TransportMode.pt, leg.getMode());
//            Assert.assertEquals(Id.create("BB", Link.class), leg.getRoute().getStartLinkId());
//            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
//            leg = legs.get(2);
//            Assert.assertEquals(TransportMode.egress_walk, leg.getMode());
//            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getStartLinkId());
//            Assert.assertEquals(Id.create("XX", Link.class), leg.getRoute().getEndLinkId());
//        }
//
//        // second check: decrease bike speed, walk should be the better option
//        // do the test this way to insure it is not accidentally correct due to the accidentally correct order the modes are initialized
//        {
//            routingModules.put(TransportMode.bike,
//                    new TeleportationRoutingModule(TransportMode.bike, f.scenario, 1.0, 1.4));
//
//            SwissRailRaptorData data = SwissRailRaptorData.create(f.scenario.getTransitSchedule(), RaptorUtils.createStaticConfig(f.config), f.scenario.getNetwork());
//            DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(null, new DefaultRaptorIntermodalAccessEgress(), routingModules);
//            SwissRailRaptor raptor = new SwissRailRaptor(data, new DefaultRaptorParametersForPerson(f.scenario.getConfig()),
//                    new LeastCostRaptorRouteSelector(), stopFinder, null );
//
//            List<Leg> legs = raptor.calcRoute(fromFac, toFac, 7 * 3600, f.dummyPerson);
//            for (Leg leg : legs) {
//                System.out.println(leg);
//            }
//
//            Assert.assertEquals("wrong number of legs.", 3, legs.size());
//            Leg leg = legs.get(0);
//            Assert.assertEquals(TransportMode.access_walk, leg.getMode());
//            Assert.assertEquals(Id.create("from", Link.class), leg.getRoute().getStartLinkId());
//            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getEndLinkId());
//            leg = legs.get(1);
//            Assert.assertEquals(TransportMode.pt, leg.getMode());
//            Assert.assertEquals(Id.create("pt_3", Link.class), leg.getRoute().getStartLinkId());
//            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getEndLinkId());
//            leg = legs.get(2);
//            Assert.assertEquals(TransportMode.egress_walk, leg.getMode());
//            Assert.assertEquals(Id.create("pt_5", Link.class), leg.getRoute().getStartLinkId());
//            Assert.assertEquals(Id.create("to", Link.class), leg.getRoute().getEndLinkId());
//        }
    }

    private static class IntermodalFixtureJakob {

        final SwissRailRaptorConfigGroup srrConfig;
        final Config config;
        final Scenario scenario;
        final Person dummyPerson;
        final Map<String, RoutingModule> routingModules;

        final double offsetB;
        double offsetC;
        final double offsetD;
        final double offsetE;

        public IntermodalFixtureJakob(double offsetB, double offsetC, double offsetD, double offsetE) {
            this.srrConfig = new SwissRailRaptorConfigGroup();
            this.config = ConfigUtils.createConfig(this.srrConfig);
            this.scenario = ScenarioUtils.createScenario(this.config);

            this.offsetB = offsetB;
            this.offsetC = offsetC;
            this.offsetD = offsetD;
            this.offsetE = offsetE;

            /* Scenario:

            (A)     (B)     (C)     (D)     (E)                                 (X)
                     \       \       \       \__________________________________/         ELine
                      \       \       \________________________________________/          DLine
                       \       \______________________________________________/           CLine
                        \____________________________________________________/            BLine

             */

            Network network = this.scenario.getNetwork();
            NetworkFactory nf = network.getFactory();

            Node nodeA = nf.createNode(Id.create("A", Node.class), new Coord(    0, 0));
            Node nodeB = nf.createNode(Id.create("B", Node.class), new Coord( 500, 0));
            Node nodeC = nf.createNode(Id.create("C", Node.class), new Coord(1000, 0));
            Node nodeD = nf.createNode(Id.create("D", Node.class), new Coord(1500, 0));
            Node nodeE = nf.createNode(Id.create("E", Node.class), new Coord(2000, 0));
            Node nodeX = nf.createNode(Id.create("X", Node.class), new Coord(100000, 0));

            network.addNode(nodeA);
            network.addNode(nodeB);
            network.addNode(nodeC);
            network.addNode(nodeD);
            network.addNode(nodeE);
            network.addNode(nodeX);

            Link linkAA = nf.createLink(Id.create("AA", Link.class), nodeA, nodeA);
            Link linkAB = nf.createLink(Id.create("AB", Link.class), nodeA, nodeB);
            Link linkBA = nf.createLink(Id.create("BA", Link.class), nodeB, nodeA);
            Link linkBB = nf.createLink(Id.create("BB", Link.class), nodeB, nodeB);
            Link linkBX = nf.createLink(Id.create("BX", Link.class), nodeB, nodeX);
            Link linkXB = nf.createLink(Id.create("XB", Link.class), nodeX, nodeB);

            Link linkCC = nf.createLink(Id.create("CC", Link.class), nodeC, nodeC);
            Link linkCX = nf.createLink(Id.create("CX", Link.class), nodeC, nodeX);
            Link linkXC = nf.createLink(Id.create("XC", Link.class), nodeX, nodeC);

            Link linkDD = nf.createLink(Id.create("DD", Link.class), nodeD, nodeD);
            Link linkDX = nf.createLink(Id.create("DX", Link.class), nodeD, nodeX);
            Link linkXD = nf.createLink(Id.create("XD", Link.class), nodeX, nodeD);

            Link linkEE = nf.createLink(Id.create("EE", Link.class), nodeE, nodeE);
            Link linkEX = nf.createLink(Id.create("EX", Link.class), nodeE, nodeX);
            Link linkXE = nf.createLink(Id.create("XE", Link.class), nodeX, nodeE);

            Link linkXX = nf.createLink(Id.create("XX", Link.class), nodeX, nodeX);

            network.addLink(linkAA);
            network.addLink(linkAB);
            network.addLink(linkBA);
            network.addLink(linkBB);
            network.addLink(linkBX);
            network.addLink(linkXB);

            network.addLink(linkCC);
            network.addLink(linkCX);
            network.addLink(linkXC);

            network.addLink(linkDD);
            network.addLink(linkDX);
            network.addLink(linkXD);

            network.addLink(linkEE);
            network.addLink(linkEX);
            network.addLink(linkXE);

            network.addLink(linkXX);

            // ----

            TransitSchedule schedule = this.scenario.getTransitSchedule();
            TransitScheduleFactory sf = schedule.getFactory();

            TransitStopFacility stopB = sf.createTransitStopFacility(Id.create("B", TransitStopFacility.class), nodeB.getCoord(), false);
            TransitStopFacility stopC = sf.createTransitStopFacility(Id.create("C", TransitStopFacility.class), nodeC.getCoord(), false);
            TransitStopFacility stopD = sf.createTransitStopFacility(Id.create("D", TransitStopFacility.class), nodeD.getCoord(), false);
            TransitStopFacility stopE = sf.createTransitStopFacility(Id.create("E", TransitStopFacility.class), nodeE.getCoord(), false);
            TransitStopFacility stopX = sf.createTransitStopFacility(Id.create("X", TransitStopFacility.class), nodeX.getCoord(), false);

            stopB.setLinkId(linkBB.getId());
            stopC.setLinkId(linkCC.getId());
            stopD.setLinkId(linkDD.getId());
            stopE.setLinkId(linkEE.getId());
            stopX.setLinkId(linkXX.getId());

            stopC.getAttributes().putAttribute("walkAccessible", "true");
            stopD.getAttributes().putAttribute("walkAccessible", "true");

            schedule.addStopFacility(stopB);
            schedule.addStopFacility(stopC);
            schedule.addStopFacility(stopD);
            schedule.addStopFacility(stopE);
            schedule.addStopFacility(stopX);

//            buildTransitLine(600., 600., 600., 600., scenario.getNetwork(), scenario.getTransitSchedule());
            // B transit line

            TransitLine BLine = sf.createTransitLine(Id.create("BLine", TransitLine.class));

            NetworkRoute networkRouteBX = RouteUtils.createLinkNetworkRouteImpl(linkBB.getId(), new Id[] { linkBX.getId() }, linkBB.getId());
            List<TransitRouteStop> stopsBX = new ArrayList<>(2);
            stopsBX.add(sf.createTransitRouteStop(stopB, Time.getUndefinedTime(), 0.0));
            stopsBX.add(sf.createTransitRouteStop(stopX, offsetB, Time.getUndefinedTime()));
            TransitRoute BXRoute = sf.createTransitRoute(Id.create("lineBX", TransitRoute.class), networkRouteBX, stopsBX, "train");
            BXRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.*3600));
            BLine.addRoute(BXRoute);

            schedule.addTransitLine(BLine);

            // C transit line

            TransitLine CLine = sf.createTransitLine(Id.create("CLine", TransitLine.class));

            NetworkRoute networkRouteCX = RouteUtils.createLinkNetworkRouteImpl(linkCC.getId(), new Id[] { linkCX.getId() }, linkCC.getId());
            List<TransitRouteStop> stopsCX = new ArrayList<>(3);
            stopsCX.add(sf.createTransitRouteStop(stopC, Time.getUndefinedTime(), 0.0));
            stopsCX.add(sf.createTransitRouteStop(stopX, offsetC, Time.getUndefinedTime()));
            TransitRoute CXRoute = sf.createTransitRoute(Id.create("lineCX", TransitRoute.class), networkRouteCX, stopsCX, "train");
            CXRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.*3600));
            CLine.addRoute(CXRoute);

            schedule.addTransitLine(CLine);

            // D transit line

            TransitLine DLine = sf.createTransitLine(Id.create("DLine", TransitLine.class));

            NetworkRoute networkRouteDX = RouteUtils.createLinkNetworkRouteImpl(linkDD.getId(), new Id[] { linkDX.getId() }, linkDD.getId());
            List<TransitRouteStop> stopsDX = new ArrayList<>(2);
            stopsDX.add(sf.createTransitRouteStop(stopD, Time.getUndefinedTime(), 0.0));
            stopsDX.add(sf.createTransitRouteStop(stopX, offsetD, Time.getUndefinedTime()));
            TransitRoute DXRoute = sf.createTransitRoute(Id.create("lineDX", TransitRoute.class), networkRouteDX, stopsDX, "train");
            DXRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.*3600));
            DLine.addRoute(DXRoute);


            schedule.addTransitLine(DLine);

            // E transit line

            TransitLine ELine = sf.createTransitLine(Id.create("ELine", TransitLine.class));

            NetworkRoute networkRouteEX = RouteUtils.createLinkNetworkRouteImpl(linkEE.getId(), new Id[] { linkEX.getId() }, linkEE.getId());
            List<TransitRouteStop> stopsEX = new ArrayList<>(2);
            stopsEX.add(sf.createTransitRouteStop(stopE, Time.getUndefinedTime(), 0.0));
            stopsEX.add(sf.createTransitRouteStop(stopX, offsetE, Time.getUndefinedTime()));
            TransitRoute EXRoute = sf.createTransitRoute(Id.create("lineEX", TransitRoute.class), networkRouteEX, stopsEX, "train");
            EXRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.*3600));
            ELine.addRoute(EXRoute);

            schedule.addTransitLine(ELine);
            // ---

            this.dummyPerson = this.scenario.getPopulation().getFactory().createPerson(Id.create("dummy", Person.class));

            // ---

            this.routingModules = new HashMap<>();
            this.routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, this.scenario, 1.1, 1.3));
            this.routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, this.scenario, 1.1, 1.3));
            this.routingModules.put(TransportMode.bike,
                    new TeleportationRoutingModule(TransportMode.bike, this.scenario, 10, 1.4)); // make bike very fast

            // we need to set special values for walk and bike as the defaults are the same for walk, bike and waiting
            // which would result in all options having the same cost in the end.
            this.config.planCalcScore().getModes().get(TransportMode.walk).setMarginalUtilityOfTraveling(-7);
            this.config.planCalcScore().getModes().get(TransportMode.bike).setMarginalUtilityOfTraveling(-8);

            this.config.transitRouter().setMaxBeelineWalkConnectionDistance(150);

            PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams(TransportMode.transit_walk);
            transitWalk.setMarginalUtilityOfTraveling(0);
            this.config.planCalcScore().addModeParams(transitWalk);

            /*
             * Prior to non_network_walk the utilities of access_walk and egress_walk were set to 0 here.
             * non_network_walk replaced access_walk and egress_walk, so one might assume that now egress_walk should
             * have marginalUtilityOfTraveling = 0.
             *
             * However, non_network_walk also replaces walk, so the alternative access leg by *_walk without any bike
             * leg is calculated based on marginalUtilityOfTraveling of non_network_walk. Setting
             * marginalUtilityOfTraveling = 0 obviously makes that alternative more attractive than any option with bike
             * could be. So set it to the utility TransportMode.walk already had before the replacement of access_walk
             * and egress_walk by non_network_walk. This should be fine as the non_network_walk legs in the path with
             * bike (and access / egress transfer) are rather short and thereby have little influence on the total cost.
             * Furthermore, this is additional cost for the path including bike, so we are on the safe side with that
             * change. - gleich aug'19
             */
            PlanCalcScoreConfigGroup.ModeParams nonNetworkWalk = new PlanCalcScoreConfigGroup.ModeParams(TransportMode.non_network_walk);
            nonNetworkWalk.setMarginalUtilityOfTraveling(-7);
            this.config.planCalcScore().addModeParams(nonNetworkWalk);

            this.srrConfig.setUseIntermodalAccessEgress(true);
//            IntermodalAccessEgressParameterSet walkAccess = new IntermodalAccessEgressParameterSet();
//            walkAccess.setMode(TransportMode.walk);
//            walkAccess.setRadius(500);
//            walkAccess.setInitialSearchRadius(500);
//            this.srrConfig.addIntermodalAccessEgress(walkAccess);

//            IntermodalAccessEgressParameterSet bikeAccess = new IntermodalAccessEgressParameterSet();
//            bikeAccess.setMode(TransportMode.bike);
//            bikeAccess.setRadius(1000);
//            bikeAccess.setInitialSearchRadius(1000);
//            bikeAccess.setStopFilterAttribute("bikeAccessible");
//            bikeAccess.setStopFilterValue("true");
//            bikeAccess.setLinkIdAttribute("accessLinkId_bike");
//            this.srrConfig.addIntermodalAccessEgress(bikeAccess);
        }

        private void buildTransitLine(double offsetB, double offsetC, double offsetD, double offsetE, Network network, TransitSchedule schedule) {
            Link linkBB = network.getLinks().get(Id.create("BB", Link.class));
            Link linkBX =  network.getLinks().get(Id.create("BX", Link.class));
            Link linkCC = network.getLinks().get(Id.create("CC", Link.class));
            Link linkCX = network.getLinks().get(Id.create("CX", Link.class));
            Link linkDD = network.getLinks().get(Id.create("DD", Link.class));
            Link linkDX = network.getLinks().get(Id.create("DX", Link.class)) ;
            Link linkEE = network.getLinks().get(Id.create("EE", Link.class));
            Link linkEX = network.getLinks().get(Id.create("EX", Link.class));
//            TransitSchedule schedule = this. ;
            TransitScheduleFactory sf = schedule.getFactory();
            TransitStopFacility stopB = schedule.getFacilities().get(Id.create("B", TransitStopFacility.class));
            TransitStopFacility stopC = schedule.getFacilities().get(Id.create("C", TransitStopFacility.class));;
            TransitStopFacility stopD = schedule.getFacilities().get(Id.create("D", TransitStopFacility.class));;
            TransitStopFacility stopE = schedule.getFacilities().get(Id.create("E", TransitStopFacility.class));;
            TransitStopFacility stopX = schedule.getFacilities().get(Id.create("X", TransitStopFacility.class));;
            // B transit line

            TransitLine BLine = sf.createTransitLine(Id.create("BLine", TransitLine.class));

            NetworkRoute networkRouteBX = RouteUtils.createLinkNetworkRouteImpl(linkBB.getId(), new Id[] { linkBX.getId() }, linkBB.getId());
            List<TransitRouteStop> stopsBX = new ArrayList<>(2);
            stopsBX.add(sf.createTransitRouteStop(stopB, Time.getUndefinedTime(), 0.0));
            stopsBX.add(sf.createTransitRouteStop(stopX, offsetB, Time.getUndefinedTime()));
            TransitRoute BXRoute = sf.createTransitRoute(Id.create("lineBX", TransitRoute.class), networkRouteBX, stopsBX, "train");
            BXRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.*3600));
            BLine.addRoute(BXRoute);

            schedule.addTransitLine(BLine);

            // C transit line

            TransitLine CLine = sf.createTransitLine(Id.create("CLine", TransitLine.class));

            NetworkRoute networkRouteCX = RouteUtils.createLinkNetworkRouteImpl(linkCC.getId(), new Id[] { linkCX.getId() }, linkCC.getId());
            List<TransitRouteStop> stopsCX = new ArrayList<>(3);
            stopsCX.add(sf.createTransitRouteStop(stopC, Time.getUndefinedTime(), 0.0));
            stopsCX.add(sf.createTransitRouteStop(stopX, offsetC, Time.getUndefinedTime()));
            TransitRoute CXRoute = sf.createTransitRoute(Id.create("lineCX", TransitRoute.class), networkRouteCX, stopsCX, "train");
            CXRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.*3600));
            CLine.addRoute(CXRoute);

            schedule.addTransitLine(CLine);

            // D transit line

            TransitLine DLine = sf.createTransitLine(Id.create("DLine", TransitLine.class));

            NetworkRoute networkRouteDX = RouteUtils.createLinkNetworkRouteImpl(linkDD.getId(), new Id[] { linkDX.getId() }, linkDD.getId());
            List<TransitRouteStop> stopsDX = new ArrayList<>(2);
            stopsDX.add(sf.createTransitRouteStop(stopD, Time.getUndefinedTime(), 0.0));
            stopsDX.add(sf.createTransitRouteStop(stopX, offsetD, Time.getUndefinedTime()));
            TransitRoute DXRoute = sf.createTransitRoute(Id.create("lineDX", TransitRoute.class), networkRouteDX, stopsDX, "train");
            DXRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.*3600));
            DLine.addRoute(DXRoute);


            schedule.addTransitLine(DLine);

            // E transit line

            TransitLine ELine = sf.createTransitLine(Id.create("ELine", TransitLine.class));

            NetworkRoute networkRouteEX = RouteUtils.createLinkNetworkRouteImpl(linkEE.getId(), new Id[] { linkEX.getId() }, linkEE.getId());
            List<TransitRouteStop> stopsEX = new ArrayList<>(2);
            stopsEX.add(sf.createTransitRouteStop(stopE, Time.getUndefinedTime(), 0.0));
            stopsEX.add(sf.createTransitRouteStop(stopX, offsetE, Time.getUndefinedTime()));
            TransitRoute EXRoute = sf.createTransitRoute(Id.create("lineEX", TransitRoute.class), networkRouteEX, stopsEX, "train");
            EXRoute.addDeparture(sf.createDeparture(Id.create("1", Departure.class), 8.*3600));
            ELine.addRoute(EXRoute);

            schedule.addTransitLine(ELine);
        }
    }

}
