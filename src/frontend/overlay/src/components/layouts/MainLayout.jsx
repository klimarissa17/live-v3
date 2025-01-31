import React from "react";
import { useSelector } from "react-redux";
import { Transition, TransitionGroup } from "react-transition-group";
import styled, { keyframes } from "styled-components";
import bg from "../../assets/images/bg.png";
import c from "../../config";
import { DEBUG } from "../../consts";
import { useQueryParams } from "../../utils/query-params";
import { StatusLightbulbs } from "../organisms/status/StatusLightbulbs";
import Advertisement from "../organisms/widgets/Advertisement";
import Pictures from "../organisms/widgets/Pictures";
import Svg from "../organisms/widgets/Svg";
import Queue from "../organisms/widgets/Queue";
import Scoreboard from "../organisms/widgets/Scoreboard";
import Ticker from "../organisms/widgets/Ticker";
import Statistics from "../organisms/widgets/Statistics";
import { TeamView } from "../organisms/widgets/TeamView";
import Videos from "../organisms/widgets/Videos";
// import PVP from "../organisms/widgets/PVP";
import FullScreenClock from "../organisms/widgets/FullScreenClock";
import Locator from "../organisms/widgets/Locator";

const fadeIn = keyframes`
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
`;

const fadeOut = keyframes`
  from {
    opacity: 1;
  }

  to {
    opacity: 0;
  }
`;

const WidgetWrap = styled.div.attrs(
    ({ left, top, width, height }) => {
        return { style: {
            left: left+"px",
            top: top+"px",
            width: width+"px",
            height: height+"px",
        } };
    }
)`
  position: absolute;
  overflow: ${({ shouldCrop = true }) => shouldCrop ? "hidden" : ""};
  animation: ${props => props.animation} ${c.WIDGET_TRANSITION_TIME}ms linear;
  animation-fill-mode: forwards;
  z-index: ${({ zIndex }) => zIndex};
`;

const MainLayoutWrap = styled.div`
  width: 1920px;
  height: 1080px;
  background: ${DEBUG ? `url(${bg})` : undefined};
`;

const transitionProps = {
    entering: { animation: fadeIn },
    entered:  {  },
    exiting:  { animation: fadeOut },
    exited:  { },
};

const WIDGETS = {
    AdvertisementWidget: Advertisement,
    ScoreboardWidget: Scoreboard,
    QueueWidget: Queue,
    PictureWidget: Pictures,
    SvgWidget: Svg,
    VideoWidget: Videos,
    TickerWidget: Ticker,
    StatisticsWidget: Statistics,
    TeamViewWidget: TeamView,
    // TeamPVPWidget: PVP, // Not actually a widget in backend.
    FullScreenClockWidget: FullScreenClock,
    TeamLocatorWidget: Locator
};

const useWidgets = () => {
    const queryParams = useQueryParams();
    if(queryParams.has("forceWidgets")) {
        return JSON.parse(queryParams.get("forceWidgets"));
    } else {
        return useSelector(state => state.widgets.widgets);
    }
};

export const MainLayout = () => {
    const widgets = useWidgets();
    const params = useQueryParams();
    return <MainLayoutWrap>
        <StatusLightbulbs compact={true}/>
        <TransitionGroup component={null}>
            {Object.values(widgets).map((obj) => {
                const Widget = WIDGETS[obj.type];
                if (Widget === undefined) {
                    return null;
                }
                if (obj.settings?.scene !== (params.get("scene") || undefined)) {
                    // FIXME: feature for multi vmix sources coordination. Should be moved to the Widget class
                    return null;
                }
                return <Transition key={obj.widgetId} timeout={Widget.overrideTimeout ?? c.WIDGET_TRANSITION_TIME}>
                    {state =>
                        state !== "exited" && <WidgetWrap
                            left={obj.location.positionX}
                            top={obj.location.positionY}
                            width={obj.location.sizeX}
                            height={obj.location.sizeY}
                            shouldCrop={Widget.shouldCrop}
                            zIndex={Widget.zIndex ?? 0}
                            {...(!Widget.ignoreAnimation && transitionProps[state])}
                        >
                            <Widget widgetData={obj} transitionState={state}/>
                        </WidgetWrap>
                    }
                </Transition>;
            })}
        </TransitionGroup>
    </MainLayoutWrap>;
};

export default MainLayout;
