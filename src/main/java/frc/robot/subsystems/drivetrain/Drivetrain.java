package frc.robot.subsystems.drivetrain;

import java.util.Optional;

import com.kauailabs.navx.frc.AHRS;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructArrayPublisher;
import edu.wpi.first.math.Matrix;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.subsystems.drivetrain.SwerveModule.DriveState;
import frc.robot.util.ChassisState;
import frc.robot.util.SwerveUtils;

public class Drivetrain extends SubsystemBase {

  private static Drivetrain instance = new Drivetrain();

  private final StructArrayPublisher<SwerveModuleState> moduleStatePublisher;
  private final StructArrayPublisher<SwerveModuleState> moduleDesiredStatePublisher; 

    public static Drivetrain getInstance() {
        return instance; 
    }

  // set up the four swerve modules  
  public SwerveModule frontLeft = new SwerveModule(Constants.DrivetrainConstants.frontLeft); 
  private SwerveModule frontRight = new SwerveModule(Constants.DrivetrainConstants.frontRight); 
  private SwerveModule backLeft = new SwerveModule(Constants.DrivetrainConstants.backLeft); 
  private SwerveModule backRight = new SwerveModule(Constants.DrivetrainConstants.backRight); 

  // initialize swerve position estimator
  private SwerveDrivePoseEstimator odometry; 

  private Pose2d lastPose = new Pose2d(); 

  // initialize the gyro on the robot
  public AHRS gyro = new AHRS(SPI.Port.kMXP);

  // represents the current drive state of the robot
  private DriveState driveState = DriveState.CLOSED_LOOP; 

  private PIDController rotationController = new PIDController(
      Constants.DrivetrainConstants.kTurn_P,
      Constants.DrivetrainConstants.kTurn_I,
      Constants.DrivetrainConstants.kTurn_D
  ); 

  private Field2d m_odometryField = new Field2d(); 
  private Field2d m_visionField = new Field2d(); 

  private Drivetrain() {

    this.rotationController.enableContinuousInput(-180, 180); 
    this.rotationController.setTolerance(Constants.DrivetrainConstants.kTurnErrorThreshold);

    // zero yaw when drivetrain first starts up
    this.gyro.zeroYaw();

    // create the pose estimator
    this.odometry = new SwerveDrivePoseEstimator(Constants.DrivetrainConstants.kDriveKinematics, gyro.getRotation2d(), getPositions(), new Pose2d()); 

    this.moduleStatePublisher = NetworkTableInstance.getDefault().getStructArrayTopic("/SwerveCurrentState", SwerveModuleState.struct).publish(); 
    this.moduleDesiredStatePublisher = NetworkTableInstance.getDefault().getStructArrayTopic("/SwerveDesiredState", SwerveModuleState.struct).publish(); 
    
  }

  @Override
  public void periodic() {
    // update the odometry with the newest rotations, positions of the swerve modules
    SmartDashboard.putNumber("drive yaw", gyro.getYaw());
    SmartDashboard.putNumber("drive x", odometry.getEstimatedPosition().getX());
    SmartDashboard.putNumber("drive y", odometry.getEstimatedPosition().getY());
    SmartDashboard.putNumber("drive omega", odometry.getEstimatedPosition().getRotation().getDegrees());

    this.odometry.update(gyro.getRotation2d(), getPositions());

    if (!SwerveUtils.isPoseValid(this.getPose()) && DriverStation.isEnabled()) {
      this.odometry.resetPosition(gyro.getRotation2d(), getPositions(), lastPose);
    }

    m_odometryField.setRobotPose(getPose());
    SmartDashboard.putData("Robot Odometry Field", m_odometryField);
    SmartDashboard.putData("Robot Vision Field", m_visionField);
    
    updateModules();
    
    lastPose = this.getPose();

    doSendables();
  }

  @Override
  public void simulationPeriodic() {
    // This method will be called once per scheduler run during simulation
  }

  public void doSendables() {
    frontLeft.doSendables();
    frontRight.doSendables();
    backLeft.doSendables();
    backRight.doSendables();

    SmartDashboard.putBoolean("Turn at desired angle", atTargetAngle());

    moduleDesiredStatePublisher.set(getDesiredStates());
    moduleStatePublisher.set(getStates());
  }

  
  /**
   * 
   * Drive the robot with a forward, sidewards, rotation speed
   * 
   * @param xMetersPerSecond the speed to drive forward with in meters per second
   * @param yMetersPerSecond the speed to drive sidewards with in meters per second
   * @param rotationRadPerSecond the speed to rotate at in radians per second
   * 
   */
  public void swerveDrive(double xMetersPerSecond, double yMetersPerSecond, double rotationRadPerSecond, boolean flipField) {
    swerveDrive(new ChassisSpeeds(
      xMetersPerSecond, 
      yMetersPerSecond, 
      rotationRadPerSecond
    ), flipField);
  }

  public void swerveDriveRobotCentric(ChassisState state) {
    
   // if (turn) lastTurnedTheta = thetaDegrees; 
    
    // always make an effort to rotate to the last angle we commanded it to

    // to commit to going to our angle even after stopping pressing
   // double rotSpeed = rotationController.calculate(this.getYaw(), lastTurnedTheta); 

    // or to not commit to the angle
    if (state.turn || gyro.getRate() > 0.25) lastTurnedTheta = this.getRotation().getDegrees(); 
    double rotSpeed = rotationController.calculate(this.getRotation().getDegrees(), state.turn ? Math.toDegrees(state.omegaRadians) : lastTurnedTheta); 
    

   rotSpeed = MathUtil.clamp(rotSpeed, -Constants.DrivetrainConstants.kMaxRotationRadPerSecond, Constants.DrivetrainConstants.kMaxRotationRadPerSecond); 

   swerveDriveRobotCentric(new ChassisSpeeds(state.vxMetersPerSecond, state.vyMetersPerSecond, rotSpeed));
  }

  public void swerveDrive(ChassisSpeeds speeds, boolean flipField) {

    Optional<Alliance> optAlliance = DriverStation.getAlliance(); 

    if (optAlliance.isEmpty()) return; 

    ChassisSpeeds robotRelative = ChassisSpeeds.fromFieldRelativeSpeeds(
      speeds, 
      optAlliance.get() == Alliance.Red && flipField ? getRotation().plus(Rotation2d.fromDegrees(180)) : getRotation() 
    ); 

    // correct for drift in the chassis
    ChassisSpeeds correctedSpeeds = SwerveUtils.correctInputWithRotation(robotRelative); 

    // calculate module states from the target speeds
    SwerveModuleState[] states = Constants.DrivetrainConstants.kDriveKinematics.toSwerveModuleStates(correctedSpeeds); 

    // ensure all speeds are reachable by the wheel
    SwerveDriveKinematics.desaturateWheelSpeeds(states, Constants.DrivetrainConstants.kMaxAttainableModuleSpeedMetersPerSecond);

    swerveDrive(states);
  }

  public void swerveDriveWithoutCompensation(ChassisSpeeds speeds) {
    SwerveModuleState[] states = Constants.DrivetrainConstants.kDriveKinematics.toSwerveModuleStates(speeds); 
    // ensure all speeds are reachable by the wheel
    SwerveDriveKinematics.desaturateWheelSpeeds(states, Constants.DrivetrainConstants.kMaxAttainableModuleSpeedMetersPerSecond);

    swerveDrive(states);
  }


  public void swerveDriveRobotCentric(ChassisSpeeds speeds) {
    // correct for drift in the chassis
    ChassisSpeeds correctedSpeeds = SwerveUtils.correctInputWithRotation(speeds); 

    // calculate module states from the target speeds
    SwerveModuleState[] states = Constants.DrivetrainConstants.kDriveKinematics.toSwerveModuleStates(correctedSpeeds); 

    // ensure all speeds are reachable by the wheel
    SwerveDriveKinematics.desaturateWheelSpeeds(states, Constants.DrivetrainConstants.kMaxAttainableModuleSpeedMetersPerSecond);

    swerveDrive(states);
  }
  private double lastTurnedTheta = 0; 

  public void resetLastTurnedTheta() {
    lastTurnedTheta = this.getRotation().getDegrees(); 
  }

  public void swerveDriveFieldRel(double xMetersPerSecond, double yMetersPerSecond, double thetaDegrees, boolean turn, boolean flipField, boolean flipRotationField) {
    
   // if (turn) lastTurnedTheta = thetaDegrees; 
    
    // always make an effort to rotate to the last angle we commanded it to

    // to commit to going to our angle even after stopping pressing
   // double rotSpeed = rotationController.calculate(this.getYaw(), lastTurnedTheta); 

    // or to not commit to the angle

    
    Optional<Alliance> optAlliance = DriverStation.getAlliance(); 

    if (optAlliance.isEmpty()) return; 

    if (flipRotationField && optAlliance.get() == Alliance.Red) thetaDegrees += 180; 
     if (turn || gyro.getRate() > 0.25) lastTurnedTheta = this.getRotation().getDegrees(); 
     double rotSpeed = rotationController.calculate(this.getRotation().getDegrees(), turn ? thetaDegrees : lastTurnedTheta); 
     

    rotSpeed = MathUtil.clamp(rotSpeed, -Constants.DrivetrainConstants.kMaxRotationRadPerSecond, Constants.DrivetrainConstants.kMaxRotationRadPerSecond); 

    swerveDrive(xMetersPerSecond, yMetersPerSecond, rotSpeed, flipField);
  }

  public void swerveDriveFieldRel(ChassisState state, boolean flipField, boolean flipRotationField) {
    swerveDriveFieldRel(state.vxMetersPerSecond, state.vyMetersPerSecond, Math.toDegrees(state.omegaRadians), state.turn, flipField, flipRotationField);
  }

  // command the swerve modules to the intended states
  public void swerveDrive(
    SwerveModuleState[] states) {
      this.frontLeft.updateState(states[0], driveState);
      this.frontRight.updateState(states[1], driveState);
      this.backLeft.updateState(states[2], driveState);
      this.backRight.updateState(states[3], driveState);
  }

  public void updateModules() {
    this.backRight.commandState();
    this.frontLeft.commandState();
    this.frontRight.commandState();
    this.backLeft.commandState();
  }

  // returns the positions of all the swerve modules
  public SwerveModulePosition[] getPositions() {
    return new SwerveModulePosition[] {
      frontLeft.getPosition(), 
      frontRight.getPosition(), 
      backLeft.getPosition(), 
      backRight.getPosition()
    }; 
  }
  
  // returns the speeds and angles of all the swerve modules
  public SwerveModuleState[] getStates() {
    return new SwerveModuleState[] {
      frontLeft.getCurrentState(), 
      frontRight.getCurrentState(), 
      backLeft.getCurrentState(), 
      backRight.getCurrentState()
    }; 
  }

  public SwerveModuleState[] getDesiredStates() {
    return new SwerveModuleState[] {
      frontLeft.getReferenceState(), 
      frontRight.getReferenceState(), 
      backLeft.getReferenceState(), 
      backRight.getReferenceState()
    }; 
  }



  // returns the speed of the robot 
  public ChassisSpeeds getChassisSpeeds() {
    return Constants.DrivetrainConstants.kDriveKinematics.toChassisSpeeds(getStates()); 
  }

  // returns the current odometry pose of the robot
  public Pose2d getPose() {
    return odometry.getEstimatedPosition(); 
  }

  public Rotation2d getRotation() {
    return this.getPose().getRotation();
  }

  public boolean atTargetAngle() {
    return this.rotationController.atSetpoint(); 
  }

  // zeros the current heading of the robot
  public void zeroHeading() {
    setHeading(Rotation2d.fromDegrees(0));
  }

  public void zeroGyroHeading() {
    
  }

  public void setHeading(Rotation2d heading) {
    resetOdometry(new Pose2d(this.getPose().getX(), this.getPose().getY(), heading));
  } 

  public Rotation2d getGyroRotation() {
    return gyro.getRotation2d(); 
  }

  // Returns the rate at which the robot is turning in degrees per second.
  public double getTurnRate() {
      return -gyro.getRate();
  }

  // reset the current pose of the robot to a set pose
  public void resetOdometry(Pose2d pose) {
    odometry.resetPosition(gyro.getRotation2d(), getPositions(), pose);
  }

  /**
   * set the drive state of the robot
   * @see DriveState
   */ 
  public void setDriveState(DriveState state) {
    this.driveState = state; 
  }

  public void addVisionPoseEstimate(Pose3d pose3d, double targetDistance, double timestamp, Matrix<N3, N1> stdDevs) {
    odometry.addVisionMeasurement(pose3d.toPose2d(), timestamp, stdDevs);

    m_visionField.setRobotPose(pose3d.toPose2d());
  }

  public Command zeroDrivetrain() {
    return Commands.runOnce(() -> {
      this.swerveDrive(0, 0, 0, false);
    }, this); 
  }
}
