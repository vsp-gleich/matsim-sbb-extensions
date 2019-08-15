package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;

import org.apache.log4j.Logger;
import org.jfree.util.Log;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.ObjectAttributes;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mrieser / Simunto GmbH
 */
public class RandomAccessEgressModeRaptorStopFinder implements RaptorStopFinder {

//	private final ObjectAttributes personAttributes;
	private final RaptorIntermodalAccessEgress intermodalAE;
	private final Map<String, RoutingModule> routingModules;
	private static final Logger log = Logger.getLogger( SwissRailRaptorCore.class ) ;

	@Inject
	public RandomAccessEgressModeRaptorStopFinder(Population population, Config config, RaptorIntermodalAccessEgress intermodalAE, Map<String, Provider<RoutingModule>> routingModuleProviders) {
//		this.personAttributes = population == null ? null : population.getPersonAttributes();
		this.intermodalAE = intermodalAE;

		SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
		this.routingModules = new HashMap<>();
		if (srrConfig.isUseIntermodalAccessEgress()) {
			for (IntermodalAccessEgressParameterSet params : srrConfig.getIntermodalAccessEgressParameterSets()) {
				String mode = params.getMode();
				this.routingModules.put(mode, routingModuleProviders.get(mode).get());
			}
		}
	}

	public RandomAccessEgressModeRaptorStopFinder(Population population, RaptorIntermodalAccessEgress intermodalAE, Map<String, RoutingModule> routingModules) {
//		this.personAttributes = population == null ? null : population.getPersonAttributes();
		this.intermodalAE = intermodalAE;
		this.routingModules = routingModules;
	}

	@Override
	public List<InitialStop> findStops(Facility facility, Person person, double departureTime, RaptorParameters parameters, SwissRailRaptorData data, RaptorStopFinder.Direction type) {
		SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
		if ( !srrCfg.isUseIntermodalAccessEgress() ) {
			log.error("RandomAccessEgressModeRaptorStopFinder is only to be used for intermodal access/egress.");
			throw new RuntimeException();
		}
		if (type == Direction.ACCESS) {
			return findIntermodalStops(facility, person, departureTime, Direction.ACCESS, parameters, data);
		}
		if (type == Direction.EGRESS) {
			return findIntermodalStops(facility, person, departureTime, Direction.EGRESS, parameters, data);
		}
		return Collections.emptyList();
	}

	private List<InitialStop> findIntermodalStops(Facility facility, Person person, double departureTime, Direction direction, RaptorParameters parameters, SwissRailRaptorData data) {
		SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
		double x = facility.getCoord().getX();
		double y = facility.getCoord().getY();
		String personId = person.getId().toString();
		List<InitialStop> initialStops = new ArrayList<>();
		int counter = 0;
		do {
			int rndSelector = (int) (MatsimRandom.getRandom().nextDouble() * srrCfg.getIntermodalAccessEgressParameterSets().size());
			addInitialStopsForParamSet(facility, person, departureTime, direction, parameters, data, x, y, personId,
					initialStops, srrCfg.getIntermodalAccessEgressParameterSets().get(rndSelector));
			counter++;
			// try again if no initial stop was found for the parameterset. Avoid infinite loop by limiting number of tries.
		} while (initialStops.isEmpty() && counter < 2 * srrCfg.getIntermodalAccessEgressParameterSets().size());
		return initialStops;
	}

	private void addInitialStopsForParamSet(Facility facility, Person person, double departureTime, Direction direction,
			RaptorParameters parameters, SwissRailRaptorData data, double x, double y, String personId,
			List<InitialStop> initialStops, IntermodalAccessEgressParameterSet paramset) {

			if ( !paramset.getDirections().contains(direction) ) {
				return;
			}
			double radius = paramset.getInitialSearchRadius();
			String mode = paramset.getMode();
			String overrideMode = null;
			if (mode.equals(TransportMode.walk) || mode.equals(TransportMode.transit_walk)) {
				// yyyyyy Das passt nicht zusammen mit meinen Annahmen.  "walk" kann durchaus ein "network walk" sein; das wuerde hier dann
				// uebergebuegelt werden.  Ist diese Abfrage denn noetig?  kai, jun'19
				// Habe gerade mal "walk" an den entscheidenden Stellen durch "bike" ersetzt; damit funktioniert es dann wie vorgesehen.  Das
				// impliziert erstmal, dass "walk" hier problematisch ist.  Ob die Weglassung woanders Probleme macht, weiss ich nicht.  kai, jun'19
				overrideMode = direction == Direction.ACCESS ? TransportMode.access_walk : TransportMode.egress_walk; 
				// verweist auf eine matsim version, wo TransportMode.access_walk noch "access_walk" und nicht "non_network_walk" ist. gl jul'19
			}
			String linkIdAttribute = paramset.getLinkIdAttribute();
			String personFilterAttribute = paramset.getPersonFilterAttribute();
			String personFilterValue = paramset.getPersonFilterValue();
			String stopFilterAttribute = paramset.getStopFilterAttribute();
			String stopFilterValue = paramset.getStopFilterValue();

			boolean personMatches = true;
			if (personFilterAttribute != null) {
//				Object attr = this.personAttributes.getAttribute(personId, personFilterAttribute);
				Object attr = person.getAttributes().getAttribute( personFilterAttribute ) ;
				String attrValue = attr == null ? null : attr.toString();
				personMatches = personFilterValue.equals(attrValue);
			}

			if (personMatches) {
				Collection<TransitStopFacility> stopFacilities = data.stopsQT.getDisk(x, y, paramset.getInitialSearchRadius());
				if (stopFacilities.size() < 2) {
					TransitStopFacility  nearestStop = data.stopsQT.getClosest(x, y);
					double nearestDistance = CoordUtils.calcEuclideanDistance(facility.getCoord(), nearestStop.getCoord());
					double newSearchRadius = Math.max( nearestDistance + paramset.getSearchExtensionRadius(), paramset.getRadius() );
					stopFacilities = data.stopsQT.getDisk(x, y, newSearchRadius);
				}
				for (TransitStopFacility stop : stopFacilities) {
					boolean filterMatches = true;
					if (stopFilterAttribute != null) {
						Object attr = stop.getAttributes().getAttribute(stopFilterAttribute);
						String attrValue = attr == null ? null : attr.toString();
						filterMatches = stopFilterValue.equals(attrValue);
					}
					if (filterMatches) {
						Facility stopFacility = stop;
						if (linkIdAttribute != null) {
							Object attr = stop.getAttributes().getAttribute(linkIdAttribute);
							if (attr != null) {
								stopFacility = new ChangedLinkFacility(stop, Id.create(attr.toString(), Link.class));
							}
						}

						List<? extends PlanElement> routeParts;
						if (direction == Direction.ACCESS) {
							RoutingModule module = this.routingModules.get(mode);
							routeParts = module.calcRoute(facility, stopFacility, departureTime, person);
						} else { // it's Egress
							// We don't know the departure time for the egress trip, so just use the original departureTime,
							// although it is wrong and might result in a wrong traveltime and thus wrong route.
							RoutingModule module = this.routingModules.get(mode);
							routeParts = module.calcRoute(stopFacility, facility, departureTime, person);
							// clear the (wrong) departureTime so users don't get confused
							for (PlanElement pe : routeParts) {
								if (pe instanceof Leg) {
									((Leg) pe).setDepartureTime(Time.getUndefinedTime());
								}
							}
						}
						if (overrideMode != null) {
							for (PlanElement pe : routeParts) {
								if (pe instanceof Leg) {
									((Leg) pe).setMode(overrideMode);
								}
							}
						}
						if (stopFacility != stop) {
							if (direction == Direction.ACCESS) {
								Leg transferLeg = PopulationUtils.createLeg(TransportMode.transit_walk);
								Route transferRoute = RouteUtils.createGenericRouteImpl(stopFacility.getLinkId(), stop.getLinkId());
								transferRoute.setTravelTime(0);
								transferRoute.setDistance(0);
								transferLeg.setRoute(transferRoute);
								transferLeg.setTravelTime(0);

								List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
								tmp.addAll(routeParts);
								tmp.add(transferLeg);
								routeParts = tmp;
							} else {
								Leg transferLeg = PopulationUtils.createLeg(TransportMode.transit_walk);
								Route transferRoute = RouteUtils.createGenericRouteImpl(stop.getLinkId(), stopFacility.getLinkId());
								transferRoute.setTravelTime(0);
								transferRoute.setDistance(0);
								transferLeg.setRoute(transferRoute);
								transferLeg.setTravelTime(0);

								List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
								tmp.add(transferLeg);
								tmp.addAll(routeParts);
								routeParts = tmp;
							}
						}
						RaptorIntermodalAccessEgress.RIntermodalAccessEgress accessEgress = this.intermodalAE.calcIntermodalAccessEgress(routeParts, parameters, person);
						InitialStop iStop = new InitialStop(stop, accessEgress.disutility, accessEgress.travelTime, accessEgress.routeParts);
						initialStops.add(iStop);
					}
				}
			}

	}

	private static class ChangedLinkFacility implements Facility, Identifiable<TransitStopFacility> {

		private final TransitStopFacility delegate;
		private final Id<Link> linkId;

		ChangedLinkFacility(final TransitStopFacility delegate, final Id<Link> linkId) {
			this.delegate = delegate;
			this.linkId = linkId;
		}

		@Override
		public Id<Link> getLinkId() {
			return this.linkId;
		}

		@Override
		public Coord getCoord() {
			return this.delegate.getCoord();
		}

		@Override
		public Map<String, Object> getCustomAttributes() {
			return this.delegate.getCustomAttributes();
		}

		@Override
		public Id<TransitStopFacility> getId() {
			return this.delegate.getId();
		}
	}
}
