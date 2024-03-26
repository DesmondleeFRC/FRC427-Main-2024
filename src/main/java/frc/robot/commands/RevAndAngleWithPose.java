package frc.robot.commands;

import java.util.Optional;
import java.util.Set; 
import java.util.function.Consumer; 

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.Constants;
import frc.robot.commands.ShootAnywhere.ShootAnywhereResult;
import frc.robot.subsystems.arm.Arm;
import frc.robot.subsystems.drivetrain.Drivetrain;
import frc.robot.subsystems.intake.Intake;
import frc.robot.subsystems.intake.commands.SetShooterSpeed;

public class RevAndAngleWithPose extends RevAndAngle {

    public Arm arm;
    public Intake intake;
    public Pose2d targetPose;
    public RevAndAngleWithPose(Arm arm, Intake intake, Pose2d target, Consumer<Rotation2d> rotation) {
        super(arm, intake, rotation); 
        this.targetPose = target;

        addRequirements(arm, intake);
    }

    public void initialize() {
        super.initialize();

        if (optAlliance.isPresent() && optAlliance.get() == Alliance.Red) {
            targetPose = new Pose2d(Constants.Vision.kAprilTagFieldLayout.getFieldLength() - targetPose.getX(), targetPose.getY(), targetPose.getRotation());
        }
    }

    public void execute() {
        super.execute();
    }

    public static Command createCommand(Arm arm, Intake intake, Pose2d targetPose, Consumer<Rotation2d> rotationConsumer) {
        return Commands.defer(() -> new RevAndAngleWithPose(arm, intake, targetPose, rotationConsumer), Set.of(arm, intake)).andThen(Commands.idle()).finallyDo(() -> 
        rotationConsumer.accept(null)); 
    }
    
    protected Pose2d getTarget() {
        return this.targetPose;
    }
} 
