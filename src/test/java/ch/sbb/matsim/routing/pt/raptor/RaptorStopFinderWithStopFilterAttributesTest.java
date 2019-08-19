package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
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
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaptorStopFinderWithStopFilterAttributesTest {
    Facility fromFac = new FakeFacility(new Coord(0, 0), Id.create("AA", Link.class)); // stop A
    Facility toFac = new FakeFacility(new Coord(100000, 0), Id.create("XX", Link.class)); // stop X

    @Test
    public void testDefaultStopFinder_StopFilterAttributes() {

        // Test 7: Test Stop Filter Attributes
        // Initial_Search_Radius includes B and C and D
        // C and D have attribute "zoomerAccessible" as "true".
        // Search_Extension_Radius is 0
        // General_Radius includes B, C, D, E
        // B and D are faster than C
        // expected: D, since it is faster and it has correct attribute
        {
            StopFinderFixture f0 = new StopFinderFixture(1., 20*60., 10*60., 1.);

            f0.scenario.getTransitSchedule().getFacilities().get(Id.create("C", TransitStopFacility.class)).getAttributes().putAttribute("zoomerAccessible", "true");
            f0.scenario.getTransitSchedule().getFacilities().get(Id.create("D", TransitStopFacility.class)).getAttributes().putAttribute("zoomerAccessible", "true");

            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f0.scenario, 5, 1.0));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f0.scenario, 5, 1.0));
            routingModules.put("zoomer",
                    new TeleportationRoutingModule("zoomer", f0.scenario, 1000., 1.));

            PlanCalcScoreConfigGroup.ModeParams modeParams = new PlanCalcScoreConfigGroup.ModeParams("zoomer");
            modeParams.setMarginalUtilityOfTraveling(0.);
            f0.scenario.getConfig().planCalcScore().addModeParams(modeParams);

            f0.srrConfig.setUseIntermodalAccessEgress(true);
            SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet zoomerAccess = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
            zoomerAccess.setMode("zoomer");
            zoomerAccess.setRadius(2000); // should not be limiting factor
            zoomerAccess.setInitialSearchRadius(1700); // Should include stops B and C and D
            zoomerAccess.setSearchExtensionRadius(0); // includes D (if neccessary)
            zoomerAccess.setStopFilterAttribute("zoomerAccessible");
            zoomerAccess.setStopFilterValue("true");
            f0.srrConfig.addIntermodalAccessEgress(zoomerAccess);

            SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet walkAccess = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(0); // should not be limiting factor
            walkAccess.setInitialSearchRadius(0); // Should include stops B and C and D
            walkAccess.setSearchExtensionRadius(0); // includes D (if neccessary)
            f0.srrConfig.addIntermodalAccessEgress(walkAccess);

            SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet nonNetworkAccess = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
            nonNetworkAccess.setMode(TransportMode.non_network_walk);
            nonNetworkAccess.setRadius(0); // should not be limiting factor
            nonNetworkAccess.setInitialSearchRadius(0); // Should include stops B and C and D
            nonNetworkAccess.setSearchExtensionRadius(0); // includes D (if neccessary)
            f0.srrConfig.addIntermodalAccessEgress(nonNetworkAccess);

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
            Assert.assertEquals("zoomer", leg.getMode());
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

        // Test 8: Test Stop Filter Attributes
        // Initial_Search_Radius includes B and C
        // C and D have attribute "zoomerAccessible" as "true".
        // Search_Extension_Radius is 600, should include D if it extends based on C (which is nearest stop with correct attribute)
        // General_Radius includes B, C, D, E
        // B and D are faster than C
        // expected: D, since it is faster and it has correct attribute
        {
            StopFinderFixture f0 = new StopFinderFixture(1., 20*60., 10*60., 1.);

            f0.scenario.getTransitSchedule().getFacilities().get(Id.create("C", TransitStopFacility.class)).getAttributes().putAttribute("zoomerAccessible", "true");
            f0.scenario.getTransitSchedule().getFacilities().get(Id.create("D", TransitStopFacility.class)).getAttributes().putAttribute("zoomerAccessible", "true");

            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f0.scenario, 5, 1.0));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f0.scenario, 5, 1.0));
            routingModules.put("zoomer",
                    new TeleportationRoutingModule("zoomer", f0.scenario, 1000., 1.));

            PlanCalcScoreConfigGroup.ModeParams modeParams = new PlanCalcScoreConfigGroup.ModeParams("zoomer");
            modeParams.setMarginalUtilityOfTraveling(0.);
            f0.scenario.getConfig().planCalcScore().addModeParams(modeParams);

            f0.srrConfig.setUseIntermodalAccessEgress(true);
            SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet zoomerAccess = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
            zoomerAccess.setMode("zoomer");
            zoomerAccess.setRadius(2000); // should not be limiting factor
            zoomerAccess.setInitialSearchRadius(1100); // Should include stops B and C and D
            zoomerAccess.setSearchExtensionRadius(600); // includes D (if neccessary)
            zoomerAccess.setStopFilterAttribute("zoomerAccessible");
            zoomerAccess.setStopFilterValue("true");
            f0.srrConfig.addIntermodalAccessEgress(zoomerAccess);

            SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet walkAccess = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(0); // should not be limiting factor
            walkAccess.setInitialSearchRadius(0); // Should include stops B and C and D
            walkAccess.setSearchExtensionRadius(0); // includes D (if neccessary)
            f0.srrConfig.addIntermodalAccessEgress(walkAccess);

//            SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet nonNetworkAccess = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
//            nonNetworkAccess.setMode(TransportMode.non_network_walk);
//            nonNetworkAccess.setRadius(0); // should not be limiting factor
//            nonNetworkAccess.setInitialSearchRadius(0); // Should include stops B and C and D
//            nonNetworkAccess.setSearchExtensionRadius(0); // includes D (if neccessary)
//            f0.srrConfig.addIntermodalAccessEgress(nonNetworkAccess);



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
            Assert.assertEquals("zoomer", leg.getMode());
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
            StopFinderFixture f0 = new StopFinderFixture(10*60., 20*60., 1., 1.);
            Map<String, RoutingModule> routingModules = new HashMap<>();
            routingModules.put(TransportMode.walk,
                    new TeleportationRoutingModule(TransportMode.walk, f0.scenario, 5., 1.0));
            routingModules.put(TransportMode.non_network_walk,
                    new TeleportationRoutingModule(TransportMode.non_network_walk, f0.scenario, 5., 1.0));
            routingModules.put(TransportMode.bike,
                    new TeleportationRoutingModule(TransportMode.bike, f0.scenario, 50., 1.0));

            f0.srrConfig.setUseIntermodalAccessEgress(true);
            SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet walkAccess = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
            walkAccess.setMode(TransportMode.walk);
            walkAccess.setRadius(10000000); // should not be limiting factor
            walkAccess.setInitialSearchRadius(1200); // Should include stops B and C
            walkAccess.setSearchExtensionRadius(2000);
            f0.srrConfig.addIntermodalAccessEgress(walkAccess);

            f0.srrConfig.setUseIntermodalAccessEgress(true);
            SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet bikeAccess = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
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
    }

    private static class StopFinderFixture {

        final SwissRailRaptorConfigGroup srrConfig;
        final Config config;
        final Scenario scenario;
        final Person dummyPerson;
        Network network ;
        NetworkFactory nf ;
//        final Map<String, RoutingModule> routingModules;

        final double offsetB;
        double offsetC;
        final double offsetD;
        final double offsetE;

        public StopFinderFixture(double offsetB, double offsetC, double offsetD, double offsetE) {
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

            this.network = this.scenario.getNetwork();
            this.nf = network.getFactory();

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

//            stopC.getAttributes().putAttribute("walkAccessible", "true");
//            stopD.getAttributes().putAttribute("walkAccessible", "true");

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

//            this.routingModules = new HashMap<>();
//            this.routingModules.put(TransportMode.walk,
//                    new TeleportationRoutingModule(TransportMode.walk, this.scenario, 1.1, 1.3));
//            this.routingModules.put(TransportMode.non_network_walk,
//                    new TeleportationRoutingModule(TransportMode.non_network_walk, this.scenario, 1.1, 1.3));
//            this.routingModules.put(TransportMode.bike,
//                    new TeleportationRoutingModule(TransportMode.bike, this.scenario, 10, 1.4)); // make bike very fast

            // we need to set special values for walk and bike as the defaults are the same for walk, bike and waiting
            // which would result in all options having the same cost in the end.


            //jr
//            this.config.planCalcScore().getModes().get(TransportMode.walk).setMarginalUtilityOfTraveling(-7);
//            this.config.planCalcScore().getModes().get(TransportMode.bike).setMarginalUtilityOfTraveling(-8);
//
//            this.config.transitRouter().setMaxBeelineWalkConnectionDistance(150);

//            PlanCalcScoreConfigGroup.ModeParams transitWalk = new PlanCalcScoreConfigGroup.ModeParams(TransportMode.transit_walk);
//            transitWalk.setMarginalUtilityOfTraveling(0);
//            this.config.planCalcScore().addModeParams(transitWalk);

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


            //jr
//            PlanCalcScoreConfigGroup.ModeParams nonNetworkWalk = new PlanCalcScoreConfigGroup.ModeParams(TransportMode.non_network_walk);
//            nonNetworkWalk.setMarginalUtilityOfTraveling(-7);
//            this.config.planCalcScore().addModeParams(nonNetworkWalk);
//
//            this.srrConfig.setUseIntermodalAccessEgress(true);


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