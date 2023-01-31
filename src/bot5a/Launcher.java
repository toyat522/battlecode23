package bot5a;

import battlecode.common.*;
import bot5a.util.*;
import bot5a.Map.Symmetry;
import bot5a.Plan.Mission;

public class Launcher extends Robot {

	void execute(RobotController rc) throws GameActionException {
		attackEnemies(rc);

		retarget(rc);

		switch (myMission.missionName) {
			case SCOUTING:
				seekAndDestroy(rc);
				break;

			case ATTACK_HQ:
				if (!seekAndDestroy(rc))
					break;
				if (rc.getLocation().distanceSquaredTo(myMission.target) > 18)
					move(rc);
				else if (rc.getLocation().distanceSquaredTo(myMission.target) <= 12) {
					Direction d = rc.getLocation().directionTo(myMission.target).opposite();
					if (rc.canMove(d))
						rc.move(d);
					else
						Scout.move(rc);
				}

				break;

			case ATTACK_ISLAND:
			case CAPTURE_ISLAND:
			case PROTECT_ISLAND:
			case COLLECT_ADAMANTIUM:
			case COLLECT_MANA:
			case PROTECT_HQ:
				if (!followLeader(rc)) {
					if (rc.getLocation().distanceSquaredTo(myMission.target) > 8)
						move(rc);
					else
						Scout.move(rc);
				}
				break;

			default:
				seekAndDestroy(rc);
		}
	}

	int tryIndex = 2;

	private void retarget(RobotController rc) throws GameActionException {
		Mission mission = Communication.readMission(rc);
		if (mission.missionName == MissionName.PROTECT_HQ) {
			myMission = mission;
			return;
		}

		int f = 0, e = 0;
		for (RobotInfo r : rc.senseNearbyRobots()) {
			if (r.getTeam() == r.getTeam())
				++f;
			else
				++e;
		}

		if (f >= 2 * e)
			myMission = new Mission(MissionName.ATTACK_HQ);
	}

	private boolean seekAndDestroy(RobotController rc) throws GameActionException {
		if (myMission.missionName != MissionName.ATTACK_HQ)
			myMission = new Mission(MissionName.ATTACK_HQ);
		for (RobotInfo r : rc.senseNearbyRobots()) {
			if (r.getType() == RobotType.HEADQUARTERS && r.getTeam() == rc.getTeam().opponent()) {
				myMission.target = r.getLocation();
				return true;
			}
		}

		myMission.target = Map.reflect(myHq, Symmetry.values()[tryIndex % 3]);

		rc.setIndicatorString("i: " + tryIndex + " guess: " + myMission.target);
		move(rc);
		if (rc.getLocation().distanceSquaredTo(myMission.target) <= 8 || turnCount % 200 == 0) {
			tryIndex++;
		}
		return false;
	}

	MapLocation[] last5 = new MapLocation[5];
	int cooldown = 0;

	private boolean isStuck(RobotController rc) throws GameActionException {
		int dx = 0;
		int dy = 0;
		MapLocation cur = rc.getLocation();
		for (int i = 0; i < 5; ++i)
			if (last5[i] != null) {
				dx += cur.x - last5[i].x;
				dy += cur.y - last5[i].y;
			}
		for (int i = 1; i < 5; ++i) {
			last5[i] = last5[i - 1];
		}
		last5[0] = cur;
		if (cooldown == 0 && dx * dx + dy * dy <= 100)
			cooldown = 10;
		if (cooldown > 0) {
			Scout.move(rc);
			--cooldown;
			return true;
		}
		return false;
	}

	private boolean followLeader(RobotController rc) throws GameActionException {
		RobotInfo leader = null;
		for (RobotInfo r : rc.senseNearbyRobots()) {
			if (r.getTeam() == rc.getTeam() && r.getType() == RobotType.LAUNCHER
					&& (leader == null || leader.getID() > r.getID()))
				leader = r;
		}
		if (leader == null || leader.getID() > rc.getID())
			return false;
		if (isStuck(rc))
			return true; // TEST: unstuck leader logic
		if (rc.getLocation().distanceSquaredTo(leader.getLocation()) > 12)
			stepTowards(rc, leader.getLocation());
		else
			Randomize.move(rc);
		return true;
	}

	private void attackEnemies(RobotController rc) throws GameActionException {
		int ACTION_RADIUS = rc.getType().actionRadiusSquared;
		Team OPPONENT = rc.getTeam().opponent();

		// Find target enemy
		RobotInfo[] enemies = rc.senseNearbyRobots(ACTION_RADIUS, OPPONENT);
		int bestScore = Integer.MIN_VALUE;
		RobotInfo target = null;
		for (RobotInfo enemy : enemies) {
			if (enemy.getType() != RobotType.HEADQUARTERS) {
				int score = 500 - enemy.getHealth();
				switch (enemy.getType()) {
					case BOOSTER:
					case DESTABILIZER:
						score *= 3;
						break;
					case LAUNCHER:
						score *= 3;
						break;
					case CARRIER:
						score *= 2;
						break;
					case AMPLIFIER:
						score *= 1;
						break;
					default:
						score *= 1;
						break;
				}
				if (score > bestScore) {
					bestScore = score;
					target = enemy;
				}
			}
		}

		if (target != null && rc.canAttack(target.getLocation()))
			rc.attack(target.getLocation());

		MapLocation clouds[] = rc.senseNearbyCloudLocations(ACTION_RADIUS);
		if (clouds != null && clouds.length > 0) {
			MapLocation targetLoc = clouds[Randomize.rng.nextInt(clouds.length)];
			if (rc.canAttack(targetLoc))
				rc.attack(targetLoc);
		}
	}
}
